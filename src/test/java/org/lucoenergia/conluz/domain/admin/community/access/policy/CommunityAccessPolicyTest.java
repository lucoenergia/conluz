package org.lucoenergia.conluz.domain.admin.community.access.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommunityAccessPolicyTest {

    private final CommunityAccessPolicy policy = new CommunityAccessPolicy();

    // --- canRead ---

    @Test
    void canRead_allows_aPlatformAdminEvenWithoutMembership() {
        assertEquals(AccessDecision.ALLOWED,
                policy.canRead(PolicyFixtures.platformAdmin(), UUID.randomUUID()));
    }

    @Test
    void canRead_allows_anEnabledMember() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED,
                policy.canRead(PolicyFixtures.memberOf(community), community.getId()));
    }

    @Test
    void canRead_isNotVisible_forANonMember() {
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canRead(PolicyFixtures.stranger(), UUID.randomUUID()));
    }

    @Test
    void canRead_isNotVisible_whenTheMembershipIsDisabled() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canRead(PolicyFixtures.disabledMemberOf(community), community.getId()));
    }

    @Test
    void canRead_isNotVisible_whenTheCallerIsNull() {
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canRead(null, UUID.randomUUID()));
    }

    // --- isMember ---

    @Test
    void isMember_allows_anEnabledMember() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED,
                policy.isMember(PolicyFixtures.memberOf(community), community.getId()));
    }

    @Test
    void isMember_allows_anEnabledCommunityAdmin() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED,
                policy.isMember(PolicyFixtures.adminOf(community), community.getId()));
    }

    @Test
    void isMember_forbids_aPlatformAdminWhoIsNotAMember() {
        // They can see the community, so this is a 403 rather than a 404.
        assertEquals(AccessDecision.FORBIDDEN,
                policy.isMember(PolicyFixtures.platformAdmin(), UUID.randomUUID()));
    }

    @Test
    void isMember_allows_aPlatformAdminWhoIsAlsoAMember() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED,
                policy.isMember(PolicyFixtures.platformAdminMemberOf(community), community.getId()));
    }

    @Test
    void isMember_isNotVisible_forANonMember() {
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.isMember(PolicyFixtures.stranger(), UUID.randomUUID()));
    }

    @Test
    void isMember_isNotVisible_whenTheMembershipIsDisabled() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.isMember(PolicyFixtures.disabledMemberOf(community), community.getId()));
    }

    // --- canManage ---

    @Test
    void canManage_allows_anEnabledCommunityAdmin() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED,
                policy.canManage(PolicyFixtures.adminOf(community), community.getId()));
    }

    @Test
    void canManage_forbids_aPlainMember() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canManage(PolicyFixtures.memberOf(community), community.getId()));
    }

    @Test
    void canManage_forbids_aPlatformAdminWhoDoesNotAdministerTheCommunity() {
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canManage(PolicyFixtures.platformAdmin(), UUID.randomUUID()));
    }

    @Test
    void canManage_isNotVisible_forANonMember() {
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canManage(PolicyFixtures.stranger(), UUID.randomUUID()));
    }

    @Test
    void canManage_isNotVisible_whenTheAdminMembershipIsDisabled() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canManage(PolicyFixtures.disabledAdminOf(community), community.getId()));
    }

    // --- canManageContact ---

    @Test
    @DisplayName("CTC-002 canManageContact allows an active community admin")
    void canManageContact_allows_anActiveCommunityAdmin() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED,
                policy.canManageContact(PolicyFixtures.adminOf(community), community.getId()));
    }

    @Test
    @DisplayName("CTC-002 canManageContact allows a platform admin who is an active community admin")
    void canManageContact_allows_aPlatformAdminWhoIsACommunityAdmin() {
        Community community = PolicyFixtures.community();
        User platformAdmin = PolicyFixtures.withMembership(PolicyFixtures.platformAdmin(), community,
                CommunityRole.COMMUNITY_ADMIN, true);
        assertEquals(AccessDecision.ALLOWED, policy.canManageContact(platformAdmin, community.getId()));
    }

    @Test
    @DisplayName("CTC-002 canManageContact forbids an active member who is not a community admin")
    void canManageContact_forbids_aPlainMember() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canManageContact(PolicyFixtures.memberOf(community), community.getId()));
    }

    @Test
    @DisplayName("CTC-002 canManageContact forbids a platform admin who is a plain member")
    void canManageContact_forbids_aPlatformAdminWhoIsAPlainMember() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canManageContact(PolicyFixtures.platformAdminMemberOf(community), community.getId()));
    }

    @Test
    @DisplayName("CTC-002 canManageContact hides the community from a platform admin who is not a member")
    void canManageContact_isNotVisible_forAPlatformAdminWhoIsNotAMember() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canManageContact(PolicyFixtures.platformAdmin(), community.getId()));
    }

    @Test
    @DisplayName("CTC-002 canManageContact hides the community from a community admin of another community")
    void canManageContact_isNotVisible_forAnAdminOfAnotherCommunity() {
        Community community = PolicyFixtures.community();
        Community other = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canManageContact(PolicyFixtures.adminOf(other), community.getId()));
    }

    @Test
    @DisplayName("CTC-002 canManageContact hides the community from a user with no membership")
    void canManageContact_isNotVisible_forAStranger() {
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canManageContact(PolicyFixtures.stranger(), UUID.randomUUID()));
    }

    @Test
    @DisplayName("CTC-002 canManageContact hides the community from an admin whose membership is disabled or pending")
    void canManageContact_isNotVisible_whenTheAdminMembershipIsNotActive() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canManageContact(PolicyFixtures.disabledAdminOf(community), community.getId()));
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canManageContact(PolicyFixtures.pendingAdminOf(community), community.getId()));
    }

    @Test
    @DisplayName("CTC-002 canManageContact hides the community from a member whose membership is disabled or pending")
    void canManageContact_isNotVisible_whenTheMembershipIsNotActive() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canManageContact(PolicyFixtures.disabledMemberOf(community), community.getId()));
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canManageContact(PolicyFixtures.pendingMemberOf(community), community.getId()));
    }

    @Test
    void canManageContact_isNotVisible_whenTheCallerIsNull() {
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canManageContact(null, UUID.randomUUID()));
    }
}
