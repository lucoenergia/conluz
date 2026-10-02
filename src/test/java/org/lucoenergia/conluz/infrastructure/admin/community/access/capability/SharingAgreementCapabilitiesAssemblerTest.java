package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.infrastructure.admin.community.access.AccessPolicies;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharingAgreementCapabilitiesAssemblerTest {

    private final SharingAgreementCapabilitiesAssembler assembler =
            new SharingAgreementCapabilitiesAssembler(new AccessPolicies());

    @Test
    void aCommunityAdminMayReadAndManageAnAgreementOfTheirPlant() {
        Community community = CapabilityFixtures.community();
        Plant plant = CapabilityFixtures.plantOf(
                CapabilityFixtures.supplyIn(community, CapabilityFixtures.stranger()));
        SharingAgreement agreement = CapabilityFixtures.agreementOf(plant);

        SharingAgreementCapabilitiesResponse capabilities =
                assembler.assemble(CapabilityFixtures.adminOf(community), plant, agreement);

        assertTrue(capabilities.isCanRead());
        assertTrue(capabilities.isCanManage());
    }

    /**
     * Agreements are admin-only for reads as well as writes: their contents are coefficients and
     * participating supplies' CUPS. A member can see the plant, so the denial is a 403 -- but a
     * capability reports only that the action is not offered.
     */
    @Test
    void aPlainMemberMayNeitherReadNorManageAnAgreement() {
        Community community = CapabilityFixtures.community();
        Plant plant = CapabilityFixtures.plantOf(
                CapabilityFixtures.supplyIn(community, CapabilityFixtures.stranger()));
        SharingAgreement agreement = CapabilityFixtures.agreementOf(plant);

        SharingAgreementCapabilitiesResponse capabilities =
                assembler.assemble(CapabilityFixtures.memberOf(community), plant, agreement);

        assertFalse(capabilities.isCanRead());
        assertFalse(capabilities.isCanManage());
    }

    /**
     * The plant gate is half the decision, and the assembler must apply it -- otherwise a caller who
     * cannot see the plant would be told they may read the agreement under it.
     */
    @Test
    void aCallerWhoCannotSeeThePlantMayDoNothingWithItsAgreement() {
        Community community = CapabilityFixtures.community();
        Plant plant = CapabilityFixtures.plantOf(
                CapabilityFixtures.supplyIn(community, CapabilityFixtures.stranger()));
        SharingAgreement agreement = CapabilityFixtures.agreementOf(plant);

        SharingAgreementCapabilitiesResponse capabilities =
                assembler.assemble(CapabilityFixtures.stranger(), plant, agreement);

        assertFalse(capabilities.isCanRead());
        assertFalse(capabilities.isCanManage());
    }

    @Test
    void anAgreementBelongingToAnotherPlantIsNotOffered() {
        Community community = CapabilityFixtures.community();
        Plant plant = CapabilityFixtures.plantOf(
                CapabilityFixtures.supplyIn(community, CapabilityFixtures.stranger()));
        Plant otherPlant = CapabilityFixtures.plantOf(
                CapabilityFixtures.supplyIn(community, CapabilityFixtures.stranger()));

        SharingAgreementCapabilitiesResponse capabilities = assembler.assemble(
                CapabilityFixtures.adminOf(community), plant, CapabilityFixtures.agreementOf(otherPlant));

        assertFalse(capabilities.isCanRead());
        assertFalse(capabilities.isCanManage());
    }
}
