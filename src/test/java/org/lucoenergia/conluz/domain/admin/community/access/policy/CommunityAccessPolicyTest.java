package org.lucoenergia.conluz.domain.admin.community.access.policy;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;

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
}
