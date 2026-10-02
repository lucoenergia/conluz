package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.infrastructure.admin.community.access.AccessPolicies;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunityCapabilitiesAssemblerTest {

    private final CommunityCapabilitiesAssembler assembler =
            new CommunityCapabilitiesAssembler(new AccessPolicies());

    @Test
    void aCommunityAdminMayDoEverythingScopedToTheirCommunity() {
        Community community = CapabilityFixtures.community();

        CommunityCapabilitiesResponse capabilities =
                assembler.assemble(CapabilityFixtures.adminOf(community), community.getId());

        assertTrue(capabilities.isCanRead());
        assertTrue(capabilities.isCanManage());
        assertTrue(capabilities.isCanManageMemberships());
        assertTrue(capabilities.isCanManageMembershipInvestment());
        assertTrue(capabilities.isCanListPlants());
        assertTrue(capabilities.isCanCreatePlants());
        assertTrue(capabilities.isCanCreateUsers());
        assertTrue(capabilities.isCanReadProduction());
        assertTrue(capabilities.isCanListSupplies());
    }

    /**
     * Enabling, disabling and updating a community are platform-wide decisions: administering a
     * community does not confer them, which is the asymmetry these three fields exist to report.
     */
    @Test
    void aCommunityAdminMayNotUpdateEnableOrDisableTheCommunity() {
        Community community = CapabilityFixtures.community();

        CommunityCapabilitiesResponse capabilities =
                assembler.assemble(CapabilityFixtures.adminOf(community), community.getId());

        assertFalse(capabilities.isCanUpdate());
        assertFalse(capabilities.isCanEnable());
        assertFalse(capabilities.isCanDisable());
    }

    @Test
    void aPlainMemberMayOnlyReadListAndSeeProduction() {
        Community community = CapabilityFixtures.community();

        CommunityCapabilitiesResponse capabilities =
                assembler.assemble(CapabilityFixtures.memberOf(community), community.getId());

        assertTrue(capabilities.isCanRead());
        assertTrue(capabilities.isCanListPlants());
        assertTrue(capabilities.isCanListSupplies());
        assertTrue(capabilities.isCanReadProduction());
        assertFalse(capabilities.isCanManage());
        assertFalse(capabilities.isCanManageMemberships());
        assertFalse(capabilities.isCanManageMembershipInvestment());
        assertFalse(capabilities.isCanCreatePlants());
        assertFalse(capabilities.isCanCreateUsers());
    }

    /**
     * A platform admin sees every community and may administer the platform's view of it, but is not
     * a member: the communal reads stay closed to them. This is the 403-not-404 asymmetry the
     * authorization policy documents, reported as capabilities.
     */
    @Test
    void aNonMemberPlatformAdminMayUpdateTheCommunityButNotReadItsCommunalData() {
        Community community = CapabilityFixtures.community();

        CommunityCapabilitiesResponse capabilities =
                assembler.assemble(CapabilityFixtures.platformAdmin(), community.getId());

        assertTrue(capabilities.isCanRead());
        assertTrue(capabilities.isCanUpdate());
        assertTrue(capabilities.isCanEnable());
        assertTrue(capabilities.isCanDisable());
        assertTrue(capabilities.isCanManageMemberships());
        assertTrue(capabilities.isCanCreateUsers());
        assertFalse(capabilities.isCanReadProduction());
        assertFalse(capabilities.isCanListSupplies());
        assertFalse(capabilities.isCanListPlants());
        assertFalse(capabilities.isCanManage());
        assertFalse(capabilities.isCanCreatePlants());
    }

    /**
     * An investment is the member's own money, so administering the platform is not administering
     * this community's finances -- the one capability a platform admin does not get here.
     */
    @Test
    void aNonMemberPlatformAdminMayNotManageMembershipInvestment() {
        Community community = CapabilityFixtures.community();

        assertFalse(assembler.assemble(CapabilityFixtures.platformAdmin(), community.getId())
                .isCanManageMembershipInvestment());
    }

    @Test
    void aStrangerMayDoNothing() {
        CommunityCapabilitiesResponse capabilities =
                assembler.assemble(CapabilityFixtures.stranger(), UUID.randomUUID());

        assertFalse(capabilities.isCanRead());
        assertFalse(capabilities.isCanUpdate());
        assertFalse(capabilities.isCanEnable());
        assertFalse(capabilities.isCanDisable());
        assertFalse(capabilities.isCanManage());
        assertFalse(capabilities.isCanManageMemberships());
        assertFalse(capabilities.isCanManageMembershipInvestment());
        assertFalse(capabilities.isCanListPlants());
        assertFalse(capabilities.isCanCreatePlants());
        assertFalse(capabilities.isCanCreateUsers());
        assertFalse(capabilities.isCanReadProduction());
        assertFalse(capabilities.isCanListSupplies());
    }

    /**
     * A disabled membership makes the community invisible to its holder everywhere else, and a
     * capability must not be the one place it does not.
     */
    @Test
    void aDisabledMemberMayDoNothing() {
        Community community = CapabilityFixtures.community();

        CommunityCapabilitiesResponse capabilities =
                assembler.assemble(CapabilityFixtures.disabledMemberOf(community), community.getId());

        assertFalse(capabilities.isCanRead());
        assertFalse(capabilities.isCanListPlants());
        assertFalse(capabilities.isCanListSupplies());
        assertFalse(capabilities.isCanReadProduction());
    }
}
