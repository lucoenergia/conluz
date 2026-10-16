package org.lucoenergia.conluz.domain.admin.community.access.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MembershipAccessPolicyTest {

    private final MembershipAccessPolicy policy = new MembershipAccessPolicy();

    // --- canManageMemberships ---

    @Test
    void canManageMemberships_allows_aPlatformAdmin() {
        assertEquals(AccessDecision.ALLOWED,
                policy.canManageMemberships(PolicyFixtures.platformAdmin(), UUID.randomUUID()));
    }

    @Test
    void canManageMemberships_allows_anEnabledCommunityAdmin() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED,
                policy.canManageMemberships(PolicyFixtures.adminOf(community), community.getId()));
    }

    @Test
    void canManageMemberships_forbids_aPlainMember() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canManageMemberships(PolicyFixtures.memberOf(community), community.getId()));
    }

    @Test
    void canManageMemberships_isNotVisible_forANonMember() {
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canManageMemberships(PolicyFixtures.stranger(), UUID.randomUUID()));
    }

    @Test
    void canManageMemberships_isNotVisible_whenTheAdminMembershipIsDisabled() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canManageMemberships(PolicyFixtures.disabledAdminOf(community), community.getId()));
    }

    @Test
    void canManageMemberships_allows_aPlatformAdminEvenWithoutACommunityId() {
        // The platform-admin branch sits after a visibility gate a platform admin always passes,
        // so a null community id is granted. Today's behaviour, reproduced deliberately.
        assertEquals(AccessDecision.ALLOWED,
                policy.canManageMemberships(PolicyFixtures.platformAdmin(), null));
    }

    @Test
    void canManageMemberships_isNotVisible_withoutACommunityIdForEveryoneElse() {
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canManageMemberships(PolicyFixtures.adminOf(PolicyFixtures.community()), null));
    }

    // --- canRegisterMembers ---
    // Only an active community admin of the community: no platform-admin bypass.

    @Test
    void canRegisterMembers_allows_anActiveCommunityAdmin() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED,
                policy.canRegisterMembers(PolicyFixtures.adminOf(community), community.getId()));
    }

    @Test
    void canRegisterMembers_forbids_aPlatformAdminWhoIsNotACommunityAdminThere() {
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canRegisterMembers(PolicyFixtures.platformAdmin(), UUID.randomUUID()));
    }

    @Test
    void canRegisterMembers_forbids_aPlatformAdminWhoIsAPlainMemberThere() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canRegisterMembers(PolicyFixtures.platformAdminMemberOf(community), community.getId()));
    }

    @Test
    void canRegisterMembers_forbids_aPlainMember() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canRegisterMembers(PolicyFixtures.memberOf(community), community.getId()));
    }

    @Test
    void canRegisterMembers_isNotVisible_forAPendingAdmin() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canRegisterMembers(PolicyFixtures.pendingAdminOf(community), community.getId()));
    }

    @Test
    void canRegisterMembers_isNotVisible_forADisabledAdmin() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canRegisterMembers(PolicyFixtures.disabledAdminOf(community), community.getId()));
    }

    @Test
    void canRegisterMembers_isNotVisible_forAnAdminOfAnotherCommunity() {
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canRegisterMembers(PolicyFixtures.adminOf(PolicyFixtures.community()), UUID.randomUUID()));
    }

    // --- canEditMemberRecords ---
    // Only an active community admin of the community: no platform-admin bypass, and no check on the target.

    @Test
    @DisplayName("MEM-028 an active community admin may edit the member records of their community")
    void canEditMemberRecords_allows_anActiveCommunityAdmin() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED,
                policy.canEditMemberRecords(PolicyFixtures.adminOf(community), community.getId()));
    }

    @Test
    @DisplayName("MEM-028 a platform admin who is not an admin of the community is forbidden")
    void canEditMemberRecords_forbids_aPlatformAdminWhoIsNotACommunityAdminThere() {
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canEditMemberRecords(PolicyFixtures.platformAdmin(), UUID.randomUUID()));
    }

    @Test
    @DisplayName("MEM-028 a platform admin who is a plain member of the community is forbidden")
    void canEditMemberRecords_forbids_aPlatformAdminWhoIsAPlainMemberThere() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canEditMemberRecords(PolicyFixtures.platformAdminMemberOf(community), community.getId()));
    }

    @Test
    @DisplayName("MEM-028 a plain member is forbidden")
    void canEditMemberRecords_forbids_aPlainMember() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canEditMemberRecords(PolicyFixtures.memberOf(community), community.getId()));
    }

    @Test
    @DisplayName("MEM-028 a pending admin cannot see the community")
    void canEditMemberRecords_isNotVisible_forAPendingAdmin() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canEditMemberRecords(PolicyFixtures.pendingAdminOf(community), community.getId()));
    }

    @Test
    @DisplayName("MEM-028 a disabled admin cannot see the community")
    void canEditMemberRecords_isNotVisible_forADisabledAdmin() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canEditMemberRecords(PolicyFixtures.disabledAdminOf(community), community.getId()));
    }

    @Test
    @DisplayName("MEM-028 an admin of another community cannot see the community")
    void canEditMemberRecords_isNotVisible_forAnAdminOfAnotherCommunity() {
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canEditMemberRecords(PolicyFixtures.adminOf(PolicyFixtures.community()), UUID.randomUUID()));
    }

    // --- canInviteMembers ---
    // The same callers as canRegisterMembers.

    @Test
    void canInviteMembers_allows_anActiveCommunityAdmin() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED,
                policy.canInviteMembers(PolicyFixtures.adminOf(community), community.getId()));
    }

    @Test
    void canInviteMembers_forbids_aPlatformAdminWhoIsNotACommunityAdminThere() {
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canInviteMembers(PolicyFixtures.platformAdmin(), UUID.randomUUID()));
    }

    @Test
    void canInviteMembers_forbids_aPlainMember() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canInviteMembers(PolicyFixtures.memberOf(community), community.getId()));
    }

    @Test
    void canInviteMembers_isNotVisible_forAPendingAdmin() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canInviteMembers(PolicyFixtures.pendingAdminOf(community), community.getId()));
    }

    @Test
    void canInviteMembers_isNotVisible_forAnAdminOfAnotherCommunity() {
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canInviteMembers(PolicyFixtures.adminOf(PolicyFixtures.community()), UUID.randomUUID()));
    }

    // --- canChangeMemberState ---

    @Test
    @DisplayName("MEM-027 an active community admin may enable and disable a member's membership")
    void canChangeMemberState_allows_anActiveCommunityAdminOnAMembersMembership() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED, policy.canChangeMemberState(PolicyFixtures.adminOf(community),
                community.getId(), membershipIn(community, CommunityRole.COMMUNITY_MEMBER)));
    }

    @Test
    @DisplayName("MEM-027 a pending member's membership may be enabled and disabled too")
    void canChangeMemberState_allows_anActiveCommunityAdminOnAPendingMembersMembership() {
        Community community = PolicyFixtures.community();
        CommunityMembership pending = PolicyFixtures.pending(
                PolicyFixtures.membershipIn(community, CommunityRole.COMMUNITY_MEMBER, true));
        assertEquals(AccessDecision.ALLOWED, policy.canChangeMemberState(PolicyFixtures.adminOf(community),
                community.getId(), () -> pending));
    }

    @Test
    @DisplayName("MEM-027 a disabled member's membership may be enabled")
    void canChangeMemberState_allows_anActiveCommunityAdminOnADisabledMembersMembership() {
        Community community = PolicyFixtures.community();
        CommunityMembership disabled = PolicyFixtures.membershipIn(community, CommunityRole.COMMUNITY_MEMBER, false);
        assertEquals(AccessDecision.ALLOWED, policy.canChangeMemberState(PolicyFixtures.adminOf(community),
                community.getId(), () -> disabled));
    }

    @Test
    @DisplayName("MEM-027 an admin's membership is not visible, as one that does not exist")
    void canChangeMemberState_isNotVisible_onAnAdminsMembership() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canChangeMemberState(PolicyFixtures.adminOf(community),
                community.getId(), membershipIn(community, CommunityRole.COMMUNITY_ADMIN)));
    }

    @Test
    @DisplayName("MEM-027 a user without a membership in the community is not visible")
    void canChangeMemberState_isNotVisible_withoutAMembership() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canChangeMemberState(PolicyFixtures.adminOf(community),
                community.getId(), () -> null));
    }

    @Test
    @DisplayName("MEM-027 a platform admin who is not an admin of the community is forbidden")
    void canChangeMemberState_forbids_aPlatformAdminWhoIsNotACommunityAdminThere() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.FORBIDDEN, policy.canChangeMemberState(PolicyFixtures.platformAdmin(),
                community.getId(), membershipIn(community, CommunityRole.COMMUNITY_MEMBER)));
        assertEquals(AccessDecision.FORBIDDEN, policy.canChangeMemberState(PolicyFixtures.platformAdminMemberOf(community),
                community.getId(), membershipIn(community, CommunityRole.COMMUNITY_MEMBER)));
    }

    @Test
    @DisplayName("MEM-027 a plain member is forbidden")
    void canChangeMemberState_forbids_aPlainMember() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.FORBIDDEN, policy.canChangeMemberState(PolicyFixtures.memberOf(community),
                community.getId(), membershipIn(community, CommunityRole.COMMUNITY_MEMBER)));
    }

    @Test
    @DisplayName("MEM-027 a pending admin, a disabled admin and an admin of another community cannot see the community")
    void canChangeMemberState_isNotVisible_forAnAdminWhoCannotSeeTheCommunity() {
        Community community = PolicyFixtures.community();
        for (User caller : new User[]{PolicyFixtures.pendingAdminOf(community),
                PolicyFixtures.disabledAdminOf(community), PolicyFixtures.adminOf(PolicyFixtures.community())}) {
            assertEquals(AccessDecision.NOT_VISIBLE, policy.canChangeMemberState(caller, community.getId(),
                    membershipIn(community, CommunityRole.COMMUNITY_MEMBER)));
        }
    }

    @Test
    void canChangeMemberState_doesNotLoadTheMembership_forACallerRefusedOnTheCommunity() {
        Community community = PolicyFixtures.community();
        AtomicInteger loads = new AtomicInteger();
        Supplier<CommunityMembership> counted = () -> {
            loads.incrementAndGet();
            return PolicyFixtures.membershipIn(community, CommunityRole.COMMUNITY_MEMBER, true);
        };

        policy.canChangeMemberState(PolicyFixtures.platformAdmin(), community.getId(), counted);
        policy.canChangeMemberState(PolicyFixtures.memberOf(community), community.getId(), counted);
        policy.canChangeMemberState(PolicyFixtures.stranger(), community.getId(), counted);

        assertEquals(0, loads.get());
    }

    private static Supplier<CommunityMembership> membershipIn(Community community, CommunityRole role) {
        CommunityMembership membership = PolicyFixtures.membershipIn(community, role, true);
        return () -> membership;
    }

        // --- canManageInvestment ---
    // No platform-admin bypass, and no forbidden-by-role outcome: a caller who is not an enabled
    // community admin here is told the community is not there.

    @Test
    void canManageInvestment_allows_anEnabledCommunityAdmin() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED,
                policy.canManageInvestment(PolicyFixtures.adminOf(community), community.getId()));
    }

    @Test
    void canManageInvestment_isNotVisible_forAPlainMember() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canManageInvestment(PolicyFixtures.memberOf(community), community.getId()));
    }

    @Test
    void canManageInvestment_isNotVisible_forAPlatformAdminWhoDoesNotAdministerTheCommunity() {
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canManageInvestment(PolicyFixtures.platformAdmin(), UUID.randomUUID()));
    }

    @Test
    void canManageInvestment_isNotVisible_whenTheAdminMembershipIsDisabled() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canManageInvestment(PolicyFixtures.disabledAdminOf(community), community.getId()));
    }

    @Test
    void canManageInvestment_forbids_whenTheCommunityIdIsMissing() {
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canManageInvestment(PolicyFixtures.platformAdmin(), null));
    }

    @Test
    void canManageInvestment_forbids_whenTheCallerIsNull() {
        assertEquals(AccessDecision.FORBIDDEN, policy.canManageInvestment(null, UUID.randomUUID()));
    }

    // --- canReadMembershipPrivateData ---

    @Test
    void canReadMembershipPrivateData_allows_aMemberReadingTheirOwnPayback() {
        Community community = PolicyFixtures.community();
        User caller = PolicyFixtures.memberOf(community);

        assertEquals(AccessDecision.ALLOWED,
                policy.canReadMembershipPrivateData(caller, community.getId(), caller.getId()));
    }

    @Test
    void canReadMembershipPrivateData_isNotVisible_whenTheirOwnMembershipIsDisabled() {
        Community community = PolicyFixtures.community();
        User caller = PolicyFixtures.disabledMemberOf(community);

        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canReadMembershipPrivateData(caller, community.getId(), caller.getId()));
    }

    @Test
    void canReadMembershipPrivateData_isNotVisible_whenReadingOwnPaybackInACommunityTheyDoNotBelongTo() {
        User caller = PolicyFixtures.memberOf(PolicyFixtures.community());

        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canReadMembershipPrivateData(caller, UUID.randomUUID(), caller.getId()));
    }

    @Test
    void canReadMembershipPrivateData_allows_anEnabledCommunityAdminReadingAnotherMember() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED,
                policy.canReadMembershipPrivateData(PolicyFixtures.adminOf(community), community.getId(), UUID.randomUUID()));
    }

    @Test
    void canReadMembershipPrivateData_isNotVisible_whenAPlainMemberReadsAnotherMember() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canReadMembershipPrivateData(PolicyFixtures.memberOf(community), community.getId(), UUID.randomUUID()));
    }

    @Test
    void canReadMembershipPrivateData_isNotVisible_forAPlatformAdminWhoIsNeitherMemberNorAdmin() {
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canReadMembershipPrivateData(PolicyFixtures.platformAdmin(), UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void canReadMembershipPrivateData_forbids_whenTheCommunityIdIsMissing() {
        User caller = PolicyFixtures.memberOf(PolicyFixtures.community());
        assertEquals(AccessDecision.FORBIDDEN, policy.canReadMembershipPrivateData(caller, null, caller.getId()));
    }

    @Test
    void canReadMembershipPrivateData_forbids_whenTheUserIdIsMissing() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canReadMembershipPrivateData(PolicyFixtures.adminOf(community), community.getId(), null));
    }

    @Test
    void canReadMembershipPrivateData_forbids_whenTheCallerIsNull() {
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canReadMembershipPrivateData(null, UUID.randomUUID(), UUID.randomUUID()));
    }
}
