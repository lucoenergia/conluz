package org.lucoenergia.conluz.domain.admin.community.access.policy;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAccessPolicyTest {

    private final UserAccessPolicy policy = new UserAccessPolicy();

    // --- canRead ---

    @Test
    void canRead_allows_aPlatformAdminToReadAnyone() {
        assertEquals(AccessDecision.ALLOWED,
                policy.canRead(PolicyFixtures.platformAdmin(), UUID.randomUUID(), noMemberships()));
    }

    @Test
    void canRead_allows_aUserToReadThemselves() {
        User caller = PolicyFixtures.stranger();
        assertEquals(AccessDecision.ALLOWED, policy.canRead(caller, caller.getId(), noMemberships()));
    }

    @Test
    void canRead_allows_aCommunityAdminToReadAMemberOfThatCommunity() {
        Community shared = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED, policy.canRead(PolicyFixtures.adminOf(shared),
                UUID.randomUUID(), memberOf(shared)));
    }

    @Test
    void canRead_isNotVisible_whenTheTargetHasNoCommunities() {
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canRead(PolicyFixtures.stranger(), UUID.randomUUID(), noMemberships()));
    }

    @Test
    void canRead_isNotVisible_whenTheTargetOnlyHasDisabledMemberships() {
        Community shared = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canRead(PolicyFixtures.adminOf(shared),
                UUID.randomUUID(), () -> List.of(
                        PolicyFixtures.membershipIn(shared, CommunityRole.COMMUNITY_MEMBER, false))));
    }

    @Test
    void canRead_isNotVisible_whenTheCallerAdminMembershipIsDisabled() {
        Community shared = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canRead(PolicyFixtures.disabledAdminOf(shared),
                UUID.randomUUID(), memberOf(shared)));
    }

    @Test
    void canRead_isNotVisible_whenTheCallerIsOnlyAMemberOfTheTargetCommunity() {
        Community shared = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canRead(PolicyFixtures.memberOf(shared),
                UUID.randomUUID(), memberOf(shared)));
    }

    // --- canEdit ---

    @Test
    void canEdit_allows_aPlatformAdmin() {
        assertEquals(AccessDecision.ALLOWED,
                policy.canEdit(PolicyFixtures.platformAdmin(), UUID.randomUUID(), noMemberships()));
    }

    @Test
    void canEdit_allows_aCommunityAdminOfTheTargetCommunity() {
        Community shared = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED, policy.canEdit(PolicyFixtures.adminOf(shared),
                UUID.randomUUID(), memberOf(shared)));
    }

    @Test
    void canEdit_isNotVisible_whenTheTargetIsOutOfReach() {
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canEdit(PolicyFixtures.stranger(), UUID.randomUUID(), noMemberships()));
    }

    @Test
    void canEdit_forbids_aPlainMemberEditingThemselves() {
        // Today's behaviour: the self branch gets them past visibility, but editing needs the
        // platform-admin or community-admin branch, so an ordinary member is forbidden.
        Community own = PolicyFixtures.community();
        User caller = PolicyFixtures.memberOf(own);

        assertEquals(AccessDecision.FORBIDDEN, policy.canEdit(caller, caller.getId(), memberOf(own)));
    }

    @Test
    void canEdit_allows_aCommunityAdminEditingThemselves() {
        Community own = PolicyFixtures.community();
        User caller = PolicyFixtures.adminOf(own);

        assertEquals(AccessDecision.ALLOWED, policy.canEdit(caller, caller.getId(),
                () -> List.of(PolicyFixtures.membershipIn(own, CommunityRole.COMMUNITY_ADMIN, true))));
    }

    // --- the target's memberships are only fetched when a branch actually needs them ---

    @Test
    void canRead_doesNotResolveTheTargetMemberships_forAPlatformAdmin() {
        AtomicInteger calls = new AtomicInteger();
        policy.canRead(PolicyFixtures.platformAdmin(), UUID.randomUUID(), counting(calls));

        assertEquals(0, calls.get());
    }

    @Test
    void canRead_doesNotResolveTheTargetMemberships_forASelfRead() {
        AtomicInteger calls = new AtomicInteger();
        User caller = PolicyFixtures.stranger();
        policy.canRead(caller, caller.getId(), counting(calls));

        assertEquals(0, calls.get());
    }

    @Test
    void canRead_resolvesTheTargetMembershipsOnce_forEveryoneElse() {
        AtomicInteger calls = new AtomicInteger();
        policy.canRead(PolicyFixtures.stranger(), UUID.randomUUID(), counting(calls));

        assertEquals(1, calls.get());
    }

    // --- canCreateIn ---

    @Test
    void canCreateIn_allows_aPlatformAdmin() {
        assertEquals(AccessDecision.ALLOWED,
                policy.canCreateIn(PolicyFixtures.platformAdmin(), UUID.randomUUID()));
    }

    @Test
    void canCreateIn_allows_aPlatformAdminEvenWithoutACommunityId() {
        // The bypass runs before the community is considered at all.
        assertEquals(AccessDecision.ALLOWED, policy.canCreateIn(PolicyFixtures.platformAdmin(), null));
    }

    @Test
    void canCreateIn_allows_anEnabledCommunityAdmin() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED,
                policy.canCreateIn(PolicyFixtures.adminOf(community), community.getId()));
    }

    @Test
    void canCreateIn_forbids_aPlainMember() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canCreateIn(PolicyFixtures.memberOf(community), community.getId()));
    }

    @Test
    void canCreateIn_forbids_whenTheCommunityIdIsMissingAndTheCallerIsNotAPlatformAdmin() {
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canCreateIn(PolicyFixtures.adminOf(PolicyFixtures.community()), null));
    }

    @Test
    void canCreateIn_isNotVisible_forANonMember() {
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canCreateIn(PolicyFixtures.stranger(), UUID.randomUUID()));
    }

    @Test
    void canCreateIn_isNotVisible_whenTheAdminMembershipIsDisabled() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canCreateIn(PolicyFixtures.disabledAdminOf(community), community.getId()));
    }

    // --- canList ---

    @Test
    void canList_allows_aPlatformAdmin() {
        assertEquals(AccessDecision.ALLOWED, policy.canList(PolicyFixtures.platformAdmin()));
    }

    @Test
    void canList_allows_anyoneWhoAdministersACommunity() {
        assertEquals(AccessDecision.ALLOWED, policy.canList(PolicyFixtures.adminOf(PolicyFixtures.community())));
    }

    @Test
    void canList_forbids_aPlainMember() {
        assertEquals(AccessDecision.FORBIDDEN, policy.canList(PolicyFixtures.memberOf(PolicyFixtures.community())));
    }

    @Test
    void canList_forbids_whenTheAdminMembershipIsDisabled() {
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canList(PolicyFixtures.disabledAdminOf(PolicyFixtures.community())));
    }

    @Test
    void canList_forbids_whenTheCallerHasNoMembershipsAtAll() {
        User caller = PolicyFixtures.stranger();
        caller.setMemberships(null);
        assertEquals(AccessDecision.FORBIDDEN, policy.canList(caller));
    }

    // --- canSee ---

    @Test
    void canSee_isTheVisibilityHalfSharedByReadAndEdit() {
        Community shared = PolicyFixtures.community();
        assertTrue(policy.canSee(PolicyFixtures.platformAdmin(), UUID.randomUUID(), noMemberships()));
        assertTrue(policy.canSee(PolicyFixtures.adminOf(shared), UUID.randomUUID(), memberOf(shared)));
        assertFalse(policy.canSee(PolicyFixtures.stranger(), UUID.randomUUID(), noMemberships()));
    }

    private Supplier<List<CommunityMembership>> noMemberships() {
        return List::of;
    }

    private Supplier<List<CommunityMembership>> memberOf(Community community) {
        return () -> List.of(PolicyFixtures.membershipIn(community, CommunityRole.COMMUNITY_MEMBER, true));
    }

    private Supplier<List<CommunityMembership>> counting(AtomicInteger calls) {
        return () -> {
            calls.incrementAndGet();
            return List.of();
        };
    }
}
