package org.lucoenergia.conluz.domain.admin.community.access.policy;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.production.plant.Plant;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlantAccessPolicyTest {

    private final PlantAccessPolicy policy = new PlantAccessPolicy(new SupplyAccessPolicy());

    // --- canRead ---
    // Plant visibility runs through membership, never canSeeCommunity, so a non-member platform
    // admin cannot see a plant at all.

    @Test
    void canRead_allows_anyEnabledMemberOfThePlantCommunity() {
        Community community = PolicyFixtures.community();
        Plant plant = plantIn(community);
        assertEquals(AccessDecision.ALLOWED, policy.canRead(PolicyFixtures.memberOf(community), plant));
    }

    @Test
    void canRead_allows_anEnabledCommunityAdmin() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED, policy.canRead(PolicyFixtures.adminOf(community), plantIn(community)));
    }

    @Test
    void canRead_isNotVisible_forAPlatformAdminWhoIsNotAMember() {
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canRead(PolicyFixtures.platformAdmin(), plantIn(PolicyFixtures.community())));
    }

    @Test
    void canRead_isNotVisible_whenThePlantDoesNotExist() {
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canRead(PolicyFixtures.platformAdmin(), null));
    }

    @Test
    void canRead_isNotVisible_whenThePlantSupplyHasNoCommunity() {
        Plant plant = PolicyFixtures.plantOf(PolicyFixtures.supplyWithoutCommunity(UUID.randomUUID()));
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canRead(PolicyFixtures.adminOf(PolicyFixtures.community()), plant));
    }

    @Test
    void canRead_isNotVisible_whenTheMembershipIsDisabled() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canRead(PolicyFixtures.disabledMemberOf(community), plantIn(community)));
    }

    // --- canManage ---

    @Test
    void canManage_allows_anEnabledCommunityAdmin() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED, policy.canManage(PolicyFixtures.adminOf(community), plantIn(community)));
    }

    @Test
    void canManage_forbids_aPlainMember() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.FORBIDDEN, policy.canManage(PolicyFixtures.memberOf(community), plantIn(community)));
    }

    @Test
    void canManage_isNotVisible_forAPlatformAdminWhoIsNotAMember() {
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canManage(PolicyFixtures.platformAdmin(), plantIn(PolicyFixtures.community())));
    }

    @Test
    void canManage_isNotVisible_whenThePlantDoesNotExist() {
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canManage(PolicyFixtures.platformAdmin(), null));
    }

    @Test
    void canManage_isNotVisible_whenThePlantSupplyHasNoCommunity() {
        Plant plant = PolicyFixtures.plantOf(PolicyFixtures.supplyWithoutCommunity(UUID.randomUUID()));
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canManage(PolicyFixtures.adminOf(PolicyFixtures.community()), plant));
    }

    // --- canCreate ---
    // The supply, not the plant, is the resource whose existence must not leak.

    @Test
    void canCreate_allows_anEnabledCommunityAdminOfTheSupplyCommunity() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED, policy.canCreate(PolicyFixtures.adminOf(community),
                PolicyFixtures.supplyIn(community, UUID.randomUUID())));
    }

    @Test
    void canCreate_forbids_theSupplyOwnerWhoIsNotACommunityAdmin() {
        // The owner can see the supply but may not put a plant on it.
        User owner = PolicyFixtures.stranger();
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canCreate(owner, PolicyFixtures.supplyIn(PolicyFixtures.community(), owner.getId())));
    }

    @Test
    void canCreate_isNotVisible_whenTheCallerCanSeeNeitherTheSupplyNorItsCommunity() {
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canCreate(PolicyFixtures.stranger(),
                PolicyFixtures.supplyIn(PolicyFixtures.community(), UUID.randomUUID())));
    }

    @Test
    void canCreate_isNotVisible_whenTheSupplyDoesNotExist() {
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canCreate(PolicyFixtures.platformAdmin(), null));
    }

    @Test
    void canCreate_isNotVisible_whenTheSupplyHasNoCommunityAndTheCallerIsNotItsOwner() {
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canCreate(PolicyFixtures.stranger(),
                PolicyFixtures.supplyWithoutCommunity(UUID.randomUUID())));
    }

    @Test
    void canCreate_forbids_theOwnerOfASupplyWithNoCommunity() {
        User owner = PolicyFixtures.stranger();
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canCreate(owner, PolicyFixtures.supplyWithoutCommunity(owner.getId())));
    }

    // --- canList ---
    // Unlike a single plant, listing goes through canSeeCommunity, so a non-member platform admin
    // gets forbidden here where they would get not-visible on a plant.

    @Test
    void canList_allows_anyEnabledMember() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.ALLOWED, policy.canList(PolicyFixtures.memberOf(community), community.getId()));
    }

    @Test
    void canList_forbids_aPlatformAdminWhoIsNotAMember() {
        assertEquals(AccessDecision.FORBIDDEN, policy.canList(PolicyFixtures.platformAdmin(), UUID.randomUUID()));
    }

    @Test
    void canList_forbids_aPlatformAdminWithoutACommunityId() {
        assertEquals(AccessDecision.FORBIDDEN, policy.canList(PolicyFixtures.platformAdmin(), null));
    }

    @Test
    void canList_isNotVisible_forANonMember() {
        assertEquals(AccessDecision.NOT_VISIBLE, policy.canList(PolicyFixtures.stranger(), UUID.randomUUID()));
    }

    @Test
    void canList_isNotVisible_whenTheMembershipIsDisabled() {
        Community community = PolicyFixtures.community();
        assertEquals(AccessDecision.NOT_VISIBLE,
                policy.canList(PolicyFixtures.disabledMemberOf(community), community.getId()));
    }

    // --- communityOf / isVisible ---

    @Test
    void communityOf_isNull_whenAnyLinkInTheChainIsMissing() {
        assertNull(policy.communityOf(null));
        assertNull(policy.communityOf(new Plant.Builder().withId(UUID.randomUUID()).build()));
        assertNull(policy.communityOf(PolicyFixtures.plantOf(
                PolicyFixtures.supplyWithoutCommunity(UUID.randomUUID()))));
    }

    @Test
    void communityOf_resolvesThroughTheSupply() {
        Community community = PolicyFixtures.community();
        assertEquals(community.getId(), policy.communityOf(plantIn(community)));
    }

    @Test
    void isVisible_isFalse_forAMissingPlant() {
        assertFalse(policy.isVisible(PolicyFixtures.platformAdmin(), null));
    }

    private Plant plantIn(Community community) {
        Supply supply = PolicyFixtures.supplyIn(community, UUID.randomUUID());
        return PolicyFixtures.plantOf(supply);
    }
}
