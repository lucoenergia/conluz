package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.infrastructure.admin.community.access.AccessPolicies;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Supply and plant capabilities together, because the interesting cases straddle the two: the owner
 * of a supply is not an admin of its plant, and a member who may list plants may not open the supply
 * behind one.
 */
class SupplyAndPlantCapabilitiesAssemblerTest {

    private final AccessPolicies policies = new AccessPolicies();
    private final SupplyCapabilitiesAssembler supplyAssembler = new SupplyCapabilitiesAssembler(policies);
    private final PlantCapabilitiesAssembler plantAssembler = new PlantCapabilitiesAssembler(policies);

    @Test
    void aCommunityAdminMayDoEverythingWithASupplyOfTheirCommunity() {
        Community community = CapabilityFixtures.community();
        Supply supply = CapabilityFixtures.supplyIn(community, CapabilityFixtures.stranger());

        SupplyCapabilitiesResponse capabilities =
                supplyAssembler.assemble(CapabilityFixtures.adminOf(community), supply);

        assertTrue(capabilities.isCanRead());
        assertTrue(capabilities.isCanEdit());
        assertTrue(capabilities.isCanReadPartitionCoefficients());
        assertTrue(capabilities.isCanCreatePlant());
    }

    /**
     * The owner can see their supply and its coefficients -- the coefficients are their own share --
     * but only a community admin may change the supply or hang a plant off it.
     */
    @Test
    void theOwnerMayReadTheirSupplyButNotEditItOrCreatePlantsOnIt() {
        Community community = CapabilityFixtures.community();
        User owner = CapabilityFixtures.memberOf(community);
        Supply supply = CapabilityFixtures.supplyIn(community, owner);

        SupplyCapabilitiesResponse capabilities = supplyAssembler.assemble(owner, supply);

        assertTrue(capabilities.isCanRead());
        assertTrue(capabilities.isCanReadPartitionCoefficients());
        assertFalse(capabilities.isCanEdit());
        assertFalse(capabilities.isCanCreatePlant());
    }

    @Test
    void aFellowMemberWhoDoesNotOwnTheSupplyMayDoNothingWithIt() {
        Community community = CapabilityFixtures.community();
        Supply supply = CapabilityFixtures.supplyIn(community, CapabilityFixtures.stranger());

        SupplyCapabilitiesResponse capabilities =
                supplyAssembler.assemble(CapabilityFixtures.memberOf(community), supply);

        assertFalse(capabilities.isCanRead());
        assertFalse(capabilities.isCanEdit());
        assertFalse(capabilities.isCanReadPartitionCoefficients());
        assertFalse(capabilities.isCanCreatePlant());
    }

    /**
     * There is no platform-admin bypass on a supply: one who neither administers the community nor
     * owns it is answered exactly like any other stranger.
     */
    @Test
    void aNonMemberPlatformAdminMayDoNothingWithASupply() {
        Community community = CapabilityFixtures.community();
        Supply supply = CapabilityFixtures.supplyIn(community, CapabilityFixtures.stranger());

        SupplyCapabilitiesResponse capabilities =
                supplyAssembler.assemble(CapabilityFixtures.platformAdmin(), supply);

        assertFalse(capabilities.isCanRead());
        assertFalse(capabilities.isCanEdit());
        assertFalse(capabilities.isCanReadPartitionCoefficients());
        assertFalse(capabilities.isCanCreatePlant());
    }

    @Test
    void aCommunityAdminMayDoEverythingWithAPlantOfTheirCommunity() {
        Community community = CapabilityFixtures.community();
        Plant plant = CapabilityFixtures.plantOf(
                CapabilityFixtures.supplyIn(community, CapabilityFixtures.stranger()));

        PlantCapabilitiesResponse capabilities =
                plantAssembler.assemble(CapabilityFixtures.adminOf(community), plant);

        assertTrue(capabilities.isCanRead());
        assertTrue(capabilities.isCanManage());
        assertTrue(capabilities.isCanListSharingAgreements());
        assertTrue(capabilities.isCanManageSharingAgreements());
        assertTrue(capabilities.isCanReadSupply());
    }

    /**
     * The case the data exposure fix turns on: a member may read the plant and see the supply
     * reference on it, but may not open that supply. The reference carries no owner, and this says
     * following it would fail.
     */
    @Test
    void aPlainMemberMayReadAPlantButNotTheSupplyBehindIt() {
        Community community = CapabilityFixtures.community();
        Plant plant = CapabilityFixtures.plantOf(
                CapabilityFixtures.supplyIn(community, CapabilityFixtures.stranger()));

        PlantCapabilitiesResponse capabilities =
                plantAssembler.assemble(CapabilityFixtures.memberOf(community), plant);

        assertTrue(capabilities.isCanRead());
        assertFalse(capabilities.isCanManage());
        assertFalse(capabilities.isCanListSharingAgreements());
        assertFalse(capabilities.isCanManageSharingAgreements());
        assertFalse(capabilities.isCanReadSupply());
    }

    @Test
    void theSupplyOwnerMayReadTheirOwnSupplyThroughThePlant() {
        Community community = CapabilityFixtures.community();
        User owner = CapabilityFixtures.memberOf(community);
        Plant plant = CapabilityFixtures.plantOf(CapabilityFixtures.supplyIn(community, owner));

        PlantCapabilitiesResponse capabilities = plantAssembler.assemble(owner, plant);

        assertTrue(capabilities.isCanRead());
        assertTrue(capabilities.isCanReadSupply());
        assertFalse(capabilities.isCanManage());
    }

    /**
     * Plant visibility runs through membership, never through canSeeCommunity, so a platform admin
     * who is not a member cannot see the plant at all.
     */
    @Test
    void aNonMemberPlatformAdminMayDoNothingWithAPlant() {
        Community community = CapabilityFixtures.community();
        Plant plant = CapabilityFixtures.plantOf(
                CapabilityFixtures.supplyIn(community, CapabilityFixtures.stranger()));

        PlantCapabilitiesResponse capabilities =
                plantAssembler.assemble(CapabilityFixtures.platformAdmin(), plant);

        assertFalse(capabilities.isCanRead());
        assertFalse(capabilities.isCanManage());
        assertFalse(capabilities.isCanListSharingAgreements());
        assertFalse(capabilities.isCanManageSharingAgreements());
        assertFalse(capabilities.isCanReadSupply());
    }
}
