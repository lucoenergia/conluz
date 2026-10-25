package org.lucoenergia.conluz.domain.admin.community.access.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.get.UserScope;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    @DisplayName("ACC-020 a platform admin may not read a user it does not administer")
    void canRead_isNotVisible_forAPlatformAdminWhoAdministersNoneOfTheTargetCommunities() {
        Community shared = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canRead(PolicyFixtures.platformAdmin(), UUID.randomUUID(), memberOf(shared)));
    }

    @Test
    @DisplayName("ACC-020 a platform admin who is a member, not an admin, of the target community may not read them")
    void canRead_isNotVisible_forAPlatformAdminWhoIsAPlainMemberOfTheTargetCommunity() {
        Community shared = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canRead(PolicyFixtures.platformAdminMemberOf(shared), UUID.randomUUID(), memberOf(shared)));
    }

    @Test
    @DisplayName("ACC-020 a platform admin who administers the target community reads them as its community admin")
    void canRead_allows_aPlatformAdminWhoAdministersTheTargetCommunity() {
        Community shared = PolicyFixtures.community();
        User caller = PolicyFixtures.adminOf(shared);
        caller.setPlatformAdmin(true);
        assertEquals(AccessDecision.ALLOWED, policy.canRead(caller, UUID.randomUUID(), memberOf(shared)));
    }

    @Test
    void canRead_allows_aPlatformAdminToReadThemselves() {
        User caller = PolicyFixtures.platformAdmin();
        assertEquals(AccessDecision.ALLOWED, policy.canRead(caller, caller.getId(), noMemberships()));
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

    // --- the target's memberships are only fetched when a branch actually needs them ---

    @Test
    void canRead_resolvesTheTargetMembershipsOnce_forAPlatformAdmin() {
        // No longer exempt: a platform admin is decided by the target's communities like anyone else.
        AtomicInteger calls = new AtomicInteger();
        policy.canRead(PolicyFixtures.platformAdmin(), UUID.randomUUID(), counting(calls));

        assertEquals(1, calls.get());
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

    // --- canListSuppliesOf ---
    // Stricter than canRead on purpose: these are supplies, and the supply rules give a platform
    // admin nothing. The user route must not undo that.

    @Test
    void canListSuppliesOf_allows_aUserAskingAboutThemselves() {
        User caller = PolicyFixtures.stranger();
        assertEquals(AccessDecision.ALLOWED,
                policy.canListSuppliesOf(caller, caller.getId(), noMemberships()));
    }

    @Test
    void canListSuppliesOf_allows_aCommunityAdminOfTheTargetCommunity() {
        Community shared = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED, policy.canListSuppliesOf(PolicyFixtures.adminOf(shared),
                UUID.randomUUID(), memberOf(shared)));
    }

    @Test
    @DisplayName("ACC-020 a platform admin who administers none of the target communities cannot see them")
    void canListSuppliesOf_isNotVisible_forAPlatformAdminWhoAdministersNoneOfTheTargetCommunities() {
        Community shared = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canListSuppliesOf(PolicyFixtures.platformAdmin(),
                UUID.randomUUID(), memberOf(shared)));
    }

    @Test
    void canListSuppliesOf_allows_aPlatformAdminWhoDoesAdministerOneOfThem() {
        Community shared = PolicyFixtures.community();
        User caller = PolicyFixtures.adminOf(shared);
        caller.setPlatformAdmin(true);

        assertEquals(AccessDecision.ALLOWED,
                policy.canListSuppliesOf(caller, UUID.randomUUID(), memberOf(shared)));
    }

    @Test
    void canListSuppliesOf_isNotVisible_whenTheCallerCannotSeeTheUserAtAll() {
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canListSuppliesOf(PolicyFixtures.stranger(), UUID.randomUUID(), noMemberships()));
    }

    @Test
    void canListSuppliesOf_isNotVisible_whenTheCallerAdminMembershipIsDisabled() {
        // Being a platform admin as well does not make the user visible.
        Community shared = PolicyFixtures.community();
        User caller = PolicyFixtures.disabledAdminOf(shared);
        caller.setPlatformAdmin(true);

        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canListSuppliesOf(caller, UUID.randomUUID(), memberOf(shared)));
    }

    @Test
    void canListSuppliesOf_isNotVisible_whenAPlainMemberAsksAboutSomeoneElse() {
        Community shared = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canListSuppliesOf(PolicyFixtures.memberOf(shared),
                UUID.randomUUID(), memberOf(shared)));
    }

    // --- canList ---

    @Test
    @DisplayName("ACC-020 a platform admin who administers no community may not list users")
    void canList_forbids_aPlatformAdminWhoAdministersNoCommunity() {
        assertEquals(AccessDecision.FORBIDDEN, policy.canList(PolicyFixtures.platformAdmin()));
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canList(PolicyFixtures.platformAdminMemberOf(PolicyFixtures.community())));
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

    // --- canEditOther ---
    // Backs deleting, enabling and disabling. Visibility first, then a refusal for everyone -- the
    // order matters, and these tests pin it.

    @Test
    @DisplayName("ACC-020 a platform admin may not enable, disable or delete a user it cannot see")
    void canEditOther_isNotVisible_forAPlatformAdminActingOnSomebodyElse() {
        Community shared = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canEditOther(PolicyFixtures.platformAdmin(), UUID.randomUUID(), memberOf(shared)));
    }

    @Test
    @DisplayName("ACC-020 a platform admin who administers the target community may not enable, disable or delete them")
    void canEditOther_isForbidden_forAPlatformAdminWhoAdministersTheTargetCommunity() {
        Community shared = PolicyFixtures.community();
        User caller = PolicyFixtures.adminOf(shared);
        caller.setPlatformAdmin(true);
        assertEquals(AccessDecision.FORBIDDEN, policy.canEditOther(caller, UUID.randomUUID(), memberOf(shared)));
    }

    @Test
    @DisplayName("ACC-008 a community admin may not enable, disable or delete a member of their community")
    void canEditOther_isForbidden_forACommunityAdminActingOnAMemberOfThatCommunity() {
        Community shared = PolicyFixtures.community();
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canEditOther(PolicyFixtures.adminOf(shared), UUID.randomUUID(), memberOf(shared)));
    }

    @Test
    @DisplayName("ACC-008 a community admin may not enable, disable or delete a platform admin of their community")
    void canEditOther_isForbidden_forACommunityAdminActingOnAPlatformAdminOfThatCommunity() {
        Community shared = PolicyFixtures.community();
        User target = PolicyFixtures.platformAdminMemberOf(shared);
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canEditOther(PolicyFixtures.adminOf(shared), target.getId(), target::getMemberships));
    }

    @Test
    void canEditOther_isForbidden_forAPlatformAdminActingOnThemselves() {
        User caller = PolicyFixtures.platformAdmin();
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canEditOther(caller, caller.getId(), noMemberships()));
    }

    @Test
    void canEditOther_isForbidden_forACommunityAdminActingOnThemselves() {
        Community shared = PolicyFixtures.community();
        User caller = PolicyFixtures.adminOf(shared);
        assertEquals(AccessDecision.FORBIDDEN, policy.canEditOther(caller, caller.getId(), memberOf(shared)));
    }

    /**
     * A plain member acting on themselves sees themselves, so they are forbidden -- not not-visible,
     * which they would be if visibility were decided by the community-admin branch alone.
     */
    @Test
    void canEditOther_isForbidden_forAPlainMemberActingOnThemselves() {
        User caller = PolicyFixtures.memberOf(PolicyFixtures.community());
        assertEquals(AccessDecision.FORBIDDEN, policy.canEditOther(caller, caller.getId(), noMemberships()));
    }

    /**
     * Visibility still comes first: a caller who cannot see the target must be told it does not
     * exist, even though the answer would have been "no" either way.
     */
    @Test
    void canEditOther_isNotVisible_whenTheCallerCannotSeeTheTarget() {
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canEditOther(PolicyFixtures.stranger(), UUID.randomUUID(), noMemberships()));
    }

    /**
     * Sharing a community is not seeing its members: only an admin of it can. So a plain member
     * acting on a fellow member is told the target does not exist, exactly as canEdit says.
     */
    @Test
    void canEditOther_isNotVisible_forAPlainMemberOfTheTargetCommunity() {
        Community shared = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canEditOther(PolicyFixtures.memberOf(shared), UUID.randomUUID(), memberOf(shared)));
    }

    // --- canSee ---

    @Test
    void canSee_isTheVisibilityHalfSharedByReadAndEdit() {
        Community shared = PolicyFixtures.community();
        assertFalse(policy.canSee(PolicyFixtures.platformAdmin(), UUID.randomUUID(), memberOf(shared)));
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

    // --- visibleUsers ---
    // The set form of canSee. The equivalence tests below are what make "a listing never carries a
    // user, or a membership, the caller could not read directly" a property of the policy.

    @Test
    @DisplayName("ACC-020 a platform admin who administers no community sees only themselves")
    void visibleUsers_isOnlyThemselves_forAPlatformAdmin() {
        User caller = PolicyFixtures.platformAdmin();
        assertEquals(UserScope.visibleTo(caller.getId(), Set.of()), policy.visibleUsers(caller));
    }

    @Test
    void visibleUsers_isThemselvesAndTheAdministeredCommunity_forAnAdminOfOneCommunity() {
        Community a = PolicyFixtures.community();
        User caller = PolicyFixtures.adminOf(a);
        assertEquals(UserScope.visibleTo(caller.getId(), Set.of(a.getId())), policy.visibleUsers(caller));
    }

    @Test
    void visibleUsers_leavesOutACommunityTheCallerOnlyBelongsTo() {
        Community a = PolicyFixtures.community();
        Community b = PolicyFixtures.community();
        User caller = withMemberships(
                PolicyFixtures.membershipIn(a, CommunityRole.COMMUNITY_ADMIN, true),
                PolicyFixtures.membershipIn(b, CommunityRole.COMMUNITY_MEMBER, true));
        assertEquals(UserScope.visibleTo(caller.getId(), Set.of(a.getId())), policy.visibleUsers(caller));
    }

    @Test
    void visibleUsers_isEveryAdministeredCommunity_forAnAdminOfSeveral() {
        Community a = PolicyFixtures.community();
        Community b = PolicyFixtures.community();
        User caller = withMemberships(
                PolicyFixtures.membershipIn(a, CommunityRole.COMMUNITY_ADMIN, true),
                PolicyFixtures.membershipIn(b, CommunityRole.COMMUNITY_ADMIN, true));
        assertEquals(UserScope.visibleTo(caller.getId(), Set.of(a.getId(), b.getId())), policy.visibleUsers(caller));
    }

    @Test
    void visibleUsers_isOnlyThemselves_forACallerWhoAdministersNothing() {
        User caller = PolicyFixtures.memberOf(PolicyFixtures.community());
        assertEquals(UserScope.visibleTo(caller.getId(), Set.of()), policy.visibleUsers(caller));
    }

    @Test
    void visibleUsers_isNobody_forAnAbsentCaller() {
        assertEquals(UserScope.visibleTo(null, Set.of()), policy.visibleUsers(null));
    }

    @Test
    void visibleUsers_includesExactlyTheUsersCanSeeAllows() {
        Matrix matrix = new Matrix();

        int included = 0;
        int excluded = 0;
        for (Map.Entry<String, User> caller : matrix.callers.entrySet()) {
            UserScope scope = policy.visibleUsers(caller.getValue());
            for (Map.Entry<String, User> user : matrix.users.entrySet()) {
                User target = user.getValue();
                boolean visible = policy.canSee(caller.getValue(), target.getId(), target::getMemberships);
                assertEquals(visible, admits(scope, target),
                        caller.getKey() + ", user " + user.getKey() + ": scope " + scope
                                + " disagrees with canSee=" + visible);
                if (visible) included++; else excluded++;
            }
        }
        // Both outcomes must occur, or the agreement above proves nothing.
        assertTrue(included > 0 && excluded > 0, "included=" + included + ", excluded=" + excluded);
    }

    @Test
    void visibleUsers_showsExactlyTheMembershipsTheCallerCouldReadDirectly() {
        // A membership can be read directly by the user it belongs to (GET /users/current) or by
        // whoever may list that community's memberships (canManageMemberships). The scope must
        // admit, on every row it lets through, exactly those.
        MembershipAccessPolicy membershipPolicy = new MembershipAccessPolicy();
        Matrix matrix = new Matrix();

        int shown = 0;
        int hidden = 0;
        for (Map.Entry<String, User> caller : matrix.callers.entrySet()) {
            UserScope scope = policy.visibleUsers(caller.getValue());
            for (Map.Entry<String, User> user : matrix.users.entrySet()) {
                User target = user.getValue();
                if (!admits(scope, target)) {
                    continue;
                }
                for (CommunityMembership membership : target.getMemberships()) {
                    boolean readable = CallerMemberships.isCurrentUser(caller.getValue(), target.getId())
                            || membershipPolicy.canManageMemberships(caller.getValue(),
                            membership.getCommunity().getId()).isAllowed();
                    assertEquals(readable, scope.includesMembershipOf(target, membership),
                            caller.getKey() + ", user " + user.getKey() + ", membership in "
                                    + membership.getCommunity().getId() + ": scope " + scope
                                    + " disagrees with readable=" + readable);
                    if (readable) shown++; else hidden++;
                }
            }
        }
        assertTrue(shown > 0 && hidden > 0, "shown=" + shown + ", hidden=" + hidden);
    }

    /**
     * Whether the scope admits this user: the in-memory reading of what {@code UserRepository.findAllVisible}
     * selects. Everyone when unrestricted, the caller, and the users with an active membership in one of the
     * scope's communities.
     */
    private static boolean admits(UserScope scope, User user) {
        if (scope.isUnrestricted() || (scope.selfId() != null && scope.selfId().equals(user.getId()))) {
            return true;
        }
        return user.getMemberships() != null && user.getMemberships().stream()
                .anyMatch(m -> m.isActive() && m.getCommunity() != null
                        && scope.communityIds().contains(m.getCommunity().getId()));
    }

    /**
     * Callers and users across communities A and B. The user "in A and B" is also a caller, so the
     * self branch is exercised by a caller who administers nothing.
     */
    private static final class Matrix {
        final Map<String, User> callers = new LinkedHashMap<>();
        final Map<String, User> users = new LinkedHashMap<>();

        Matrix() {
            Community a = PolicyFixtures.community();
            Community b = PolicyFixtures.community();

            users.put("in A", PolicyFixtures.memberOf(a));
            users.put("in B", PolicyFixtures.memberOf(b));
            users.put("in A and B", withMemberships(
                    PolicyFixtures.membershipIn(a, CommunityRole.COMMUNITY_MEMBER, true),
                    PolicyFixtures.membershipIn(b, CommunityRole.COMMUNITY_MEMBER, true)));
            users.put("in no community", PolicyFixtures.stranger());
            users.put("disabled in A", PolicyFixtures.disabledMemberOf(a));
            users.put("pending in A", PolicyFixtures.pendingMemberOf(a));

            callers.put("platform admin", PolicyFixtures.platformAdmin());
            callers.put("platform admin, member of A", PolicyFixtures.platformAdminMemberOf(a));
            callers.put("admin of A", PolicyFixtures.adminOf(a));
            callers.put("admin of A and B", withMemberships(
                    PolicyFixtures.membershipIn(a, CommunityRole.COMMUNITY_ADMIN, true),
                    PolicyFixtures.membershipIn(b, CommunityRole.COMMUNITY_ADMIN, true)));
            callers.put("admin of A, member of B", withMemberships(
                    PolicyFixtures.membershipIn(a, CommunityRole.COMMUNITY_ADMIN, true),
                    PolicyFixtures.membershipIn(b, CommunityRole.COMMUNITY_MEMBER, true)));
            callers.put("plain member of A", PolicyFixtures.memberOf(a));
            callers.put("disabled admin of A", PolicyFixtures.disabledAdminOf(a));
            callers.put("pending admin of A", PolicyFixtures.pendingAdminOf(a));
            callers.put("the user in A and B", users.get("in A and B"));
            callers.put("stranger", PolicyFixtures.stranger());
        }
    }

    private static User withMemberships(CommunityMembership... memberships) {
        User user = PolicyFixtures.stranger();
        user.setMemberships(List.of(memberships));
        return user;
    }
}
