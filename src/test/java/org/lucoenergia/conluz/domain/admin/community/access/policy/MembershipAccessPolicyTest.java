package org.lucoenergia.conluz.domain.admin.community.access.policy;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.UUID;

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
