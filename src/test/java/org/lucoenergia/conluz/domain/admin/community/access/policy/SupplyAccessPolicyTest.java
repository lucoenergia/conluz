package org.lucoenergia.conluz.domain.admin.community.access.policy;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupplyAccessPolicyTest {

    private final SupplyAccessPolicy policy = new SupplyAccessPolicy();

    // --- canRead ---
    // Reading and editing need the same access, so anyone who may not read gets not-visible, never
    // forbidden: a 403 would confirm the supply exists.

    @Test
    void canRead_allows_theOwner() {
        User owner = PolicyFixtures.stranger();
        assertEquals(AccessDecision.ALLOWED,
                policy.canRead(owner, PolicyFixtures.supplyWithoutCommunity(owner.getId())));
    }

    @Test
    void canRead_allows_anEnabledCommunityAdminOfTheSupplyCommunity() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED, policy.canRead(PolicyFixtures.adminOf(community),
                PolicyFixtures.supplyIn(community, UUID.randomUUID())));
    }

    @Test
    void canRead_isNotVisible_forAPlainMemberOfTheSupplyCommunity() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canRead(PolicyFixtures.memberOf(community),
                PolicyFixtures.supplyIn(community, UUID.randomUUID())));
    }

    @Test
    void canRead_isNotVisible_forAPlatformAdminWhoNeitherOwnsNorAdministers() {
        // No platform-admin bypass anywhere in supply access.
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canRead(PolicyFixtures.platformAdmin(),
                PolicyFixtures.supplyIn(PolicyFixtures.community(), UUID.randomUUID())));
    }

    @Test
    void canRead_isNotVisible_whenTheSupplyDoesNotExist() {
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canRead(PolicyFixtures.platformAdmin(), null));
    }

    @Test
    void canRead_isNotVisible_whenTheAdminMembershipIsDisabled() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canRead(PolicyFixtures.disabledAdminOf(community),
                PolicyFixtures.supplyIn(community, UUID.randomUUID())));
    }

    // --- canEdit ---

    @Test
    void canEdit_allows_anEnabledCommunityAdmin() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED, policy.canEdit(PolicyFixtures.adminOf(community),
                PolicyFixtures.supplyIn(community, UUID.randomUUID())));
    }

    @Test
    void canEdit_forbids_theOwnerWhoIsNotACommunityAdmin() {
        // The owner can see the supply, so this is the one supply denial that is a 403.
        User owner = PolicyFixtures.stranger();
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canEdit(owner, PolicyFixtures.supplyWithoutCommunity(owner.getId())));
    }

    @Test
    void canEdit_allows_anOwnerWhoIsAlsoTheCommunityAdmin() {
        Community community = PolicyFixtures.community();
        User owner = PolicyFixtures.adminOf(community);
        assertEquals(AccessDecision.ALLOWED,
                policy.canEdit(owner, PolicyFixtures.supplyIn(community, owner.getId())));
    }

    @Test
    void canEdit_isNotVisible_forAPlainMemberOfTheSupplyCommunity() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canEdit(PolicyFixtures.memberOf(community),
                PolicyFixtures.supplyIn(community, UUID.randomUUID())));
    }

    @Test
    void canEdit_isNotVisible_whenTheSupplyDoesNotExist() {
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canEdit(PolicyFixtures.platformAdmin(), null));
    }

    // --- canReadPartitionCoefficients ---
    // Byte-for-byte canEdit today; kept separate so the two can diverge later.

    @Test
    void canReadPartitionCoefficients_matchesCanEdit_forEveryCaller() {
        Community community = PolicyFixtures.community();
        User admin = PolicyFixtures.adminOf(community);
        User member = PolicyFixtures.memberOf(community);
        User owner = PolicyFixtures.stranger();
        Supply supplyInCommunity = PolicyFixtures.supplyIn(community, UUID.randomUUID());
        Supply ownedSupply = PolicyFixtures.supplyWithoutCommunity(owner.getId());

        assertEquals(policy.canEdit(admin, supplyInCommunity),
                policy.canReadPartitionCoefficients(admin, supplyInCommunity));
        assertEquals(policy.canEdit(member, supplyInCommunity),
                policy.canReadPartitionCoefficients(member, supplyInCommunity));
        assertEquals(policy.canEdit(owner, ownedSupply),
                policy.canReadPartitionCoefficients(owner, ownedSupply));
        assertEquals(policy.canEdit(admin, null), policy.canReadPartitionCoefficients(admin, null));
    }

    @Test
    void canReadPartitionCoefficients_allows_anEnabledCommunityAdmin() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED, policy.canReadPartitionCoefficients(
                PolicyFixtures.adminOf(community), PolicyFixtures.supplyIn(community, UUID.randomUUID())));
    }

    @Test
    void canReadPartitionCoefficients_forbids_theOwnerWhoIsNotACommunityAdmin() {
        User owner = PolicyFixtures.stranger();
        assertEquals(AccessDecision.FORBIDDEN, policy.canReadPartitionCoefficients(
                owner, PolicyFixtures.supplyWithoutCommunity(owner.getId())));
    }

    // --- isCommunityAdminOfSupply ---
    // A shaping question asked after a gate has passed, so it never answers not-visible.

    @Test
    void isCommunityAdminOfSupply_allows_anEnabledCommunityAdmin() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED, policy.isCommunityAdminOfSupply(
                PolicyFixtures.adminOf(community), PolicyFixtures.supplyIn(community, UUID.randomUUID())));
    }

    @Test
    void isCommunityAdminOfSupply_allows_anOwnerWhoIsAlsoTheCommunityAdmin() {
        Community community = PolicyFixtures.community();
        User owner = PolicyFixtures.adminOf(community);
        assertEquals(AccessDecision.ALLOWED,
                policy.isCommunityAdminOfSupply(owner, PolicyFixtures.supplyIn(community, owner.getId())));
    }

    @Test
    void isCommunityAdminOfSupply_forbids_theOwnerAlone() {
        User owner = PolicyFixtures.stranger();
        assertEquals(AccessDecision.FORBIDDEN, policy.isCommunityAdminOfSupply(
                owner, PolicyFixtures.supplyWithoutCommunity(owner.getId())));
    }

    @Test
    void isCommunityAdminOfSupply_forbids_aPlatformAdminWhoDoesNotAdministerTheCommunity() {
        assertEquals(AccessDecision.FORBIDDEN, policy.isCommunityAdminOfSupply(
                PolicyFixtures.platformAdmin(), PolicyFixtures.supplyIn(PolicyFixtures.community(), UUID.randomUUID())));
    }

    @Test
    void isCommunityAdminOfSupply_forbids_whenTheSupplyDoesNotExist() {
        assertEquals(AccessDecision.FORBIDDEN,
                policy.isCommunityAdminOfSupply(PolicyFixtures.platformAdmin(), null));
    }

    @Test
    void isCommunityAdminOfSupply_forbids_whenTheSupplyHasNoCommunity() {
        assertEquals(AccessDecision.FORBIDDEN, policy.isCommunityAdminOfSupply(
                PolicyFixtures.adminOf(PolicyFixtures.community()),
                PolicyFixtures.supplyWithoutCommunity(UUID.randomUUID())));
    }

    // --- the shared predicates ---

    @Test
    void isVisible_isFalse_forAMissingSupply() {
        assertFalse(policy.isVisible(PolicyFixtures.platformAdmin(), null));
    }

    @Test
    void isOwner_comparesTheSupplyOwnerToTheCaller() {
        User owner = PolicyFixtures.stranger();
        assertTrue(policy.isOwner(owner, PolicyFixtures.supplyWithoutCommunity(owner.getId())));
        assertFalse(policy.isOwner(owner, PolicyFixtures.supplyWithoutCommunity(UUID.randomUUID())));
    }

    @Test
    void isOwner_isFalse_whenTheSupplyHasNoOwnerId() {
        assertFalse(policy.isOwner(PolicyFixtures.stranger(), PolicyFixtures.supplyWithoutCommunity(null)));
    }
}
