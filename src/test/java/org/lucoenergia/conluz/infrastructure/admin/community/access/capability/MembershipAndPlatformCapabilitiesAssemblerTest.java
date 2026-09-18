package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.access.AccessPolicies;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MembershipAndPlatformCapabilitiesAssemblerTest {

    private final AccessPolicies policies = new AccessPolicies();
    private final MembershipCapabilitiesAssembler membershipAssembler =
            new MembershipCapabilitiesAssembler(policies);
    private final PlatformCapabilitiesAssembler platformAssembler =
            new PlatformCapabilitiesAssembler(policies);

    @Test
    void aCommunityAdminMayManageAMembershipOfTheirCommunity() {
        Community community = CapabilityFixtures.community();
        CommunityMembership membership = CapabilityFixtures.membershipOf(
                CapabilityFixtures.stranger(), community, CommunityRole.COMMUNITY_MEMBER, true);

        MembershipCapabilitiesResponse capabilities =
                membershipAssembler.assemble(CapabilityFixtures.adminOf(community), membership);

        assertTrue(capabilities.isCanUpdateRole());
        assertTrue(capabilities.isCanDelete());
        assertTrue(capabilities.isCanManageInvestment());
        assertTrue(capabilities.isCanReadPayback());
    }

    /**
     * The roster is administrative, but a member's own payback is theirs to read -- the one
     * capability a plain member gets on their own membership.
     */
    @Test
    void aPlainMemberMayReadOnlyTheirOwnPayback() {
        Community community = CapabilityFixtures.community();
        User member = CapabilityFixtures.memberOf(community);
        CommunityMembership ownMembership =
                CapabilityFixtures.membershipOf(member, community, CommunityRole.COMMUNITY_MEMBER, true);

        MembershipCapabilitiesResponse capabilities = membershipAssembler.assemble(member, ownMembership);

        assertTrue(capabilities.isCanReadPayback());
        assertFalse(capabilities.isCanUpdateRole());
        assertFalse(capabilities.isCanDelete());
        assertFalse(capabilities.isCanManageInvestment());
    }

    @Test
    void aPlainMemberMayNotReadSomebodyElsesPayback() {
        Community community = CapabilityFixtures.community();
        CommunityMembership somebodyElse = CapabilityFixtures.membershipOf(
                CapabilityFixtures.stranger(), community, CommunityRole.COMMUNITY_MEMBER, true);

        assertFalse(membershipAssembler.assemble(CapabilityFixtures.memberOf(community), somebodyElse)
                .isCanReadPayback());
    }

    /**
     * Administering the platform is not administering a community's finances, and it is not
     * membership either: the platform admin gets the roster and nothing else.
     */
    @Test
    void aNonMemberPlatformAdminMayManageTheRosterButNotInvestmentOrPayback() {
        Community community = CapabilityFixtures.community();
        CommunityMembership membership = CapabilityFixtures.membershipOf(
                CapabilityFixtures.stranger(), community, CommunityRole.COMMUNITY_MEMBER, true);

        MembershipCapabilitiesResponse capabilities =
                membershipAssembler.assemble(CapabilityFixtures.platformAdmin(), membership);

        assertTrue(capabilities.isCanUpdateRole());
        assertTrue(capabilities.isCanDelete());
        assertFalse(capabilities.isCanManageInvestment());
        assertFalse(capabilities.isCanReadPayback());
    }

    @Test
    void aPlatformAdminMayCreateCommunitiesAndListUsers() {
        PlatformCapabilitiesResponse capabilities =
                platformAssembler.assemble(CapabilityFixtures.platformAdmin());

        assertTrue(capabilities.isCanCreateCommunity());
        assertTrue(capabilities.isCanListUsers());
    }

    /**
     * Listing users is open to anyone who administers any community -- scoping the listing is the
     * endpoint's job -- while creating a community is not.
     */
    @Test
    void aCommunityAdminMayListUsersButNotCreateCommunities() {
        PlatformCapabilitiesResponse capabilities =
                platformAssembler.assemble(CapabilityFixtures.adminOf(CapabilityFixtures.community()));

        assertFalse(capabilities.isCanCreateCommunity());
        assertTrue(capabilities.isCanListUsers());
    }

    @Test
    void aPlainMemberMayDoNeither() {
        PlatformCapabilitiesResponse capabilities =
                platformAssembler.assemble(CapabilityFixtures.memberOf(CapabilityFixtures.community()));

        assertFalse(capabilities.isCanCreateCommunity());
        assertFalse(capabilities.isCanListUsers());
    }
}
