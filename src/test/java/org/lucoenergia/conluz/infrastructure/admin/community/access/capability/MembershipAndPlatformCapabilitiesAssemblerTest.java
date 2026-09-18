package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.access.AccessPolicies;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void aPlatformAdminGetsEveryPlatformCapability() {
        PlatformCapabilitiesResponse capabilities =
                platformAssembler.assemble(CapabilityFixtures.platformAdmin());

        assertTrue(capabilities.isCanCreateCommunity());
        assertTrue(capabilities.isCanListUsers());
        assertTrue(capabilities.isCanAdministerPlatform());
        assertTrue(capabilities.isCanCreateUsers());
    }

    /**
     * Belonging to a community neither adds to nor subtracts from what a platform admin may do on
     * the platform -- the row where almost every asymmetry in this codebase turns, and the one the
     * other three cases cannot reach.
     */
    @Test
    void aPlatformAdminInsideACommunityGetsTheSamePlatformCapabilities() {
        PlatformCapabilitiesResponse capabilities = platformAssembler.assemble(
                CapabilityFixtures.platformAdminMemberOf(CapabilityFixtures.community()));

        assertTrue(capabilities.isCanCreateCommunity());
        assertTrue(capabilities.isCanListUsers());
        assertTrue(capabilities.isCanAdministerPlatform());
        assertTrue(capabilities.isCanCreateUsers());
    }

    /**
     * Listing users is open to anyone who administers any community -- scoping the listing is the
     * endpoint's job -- while the other three are platform-wide. Creating users is the sharpest of
     * them: a community admin may create users <em>in their own community</em>
     * (CommunityCapabilitiesResponse.canCreateUsers), but not a user belonging to none.
     */
    @Test
    void aCommunityAdminMayOnlyListUsers() {
        PlatformCapabilitiesResponse capabilities =
                platformAssembler.assemble(CapabilityFixtures.adminOf(CapabilityFixtures.community()));

        assertTrue(capabilities.isCanListUsers());
        assertFalse(capabilities.isCanCreateCommunity());
        assertFalse(capabilities.isCanAdministerPlatform());
        assertFalse(capabilities.isCanCreateUsers());
    }

    @Test
    void aPlainMemberGetsNoPlatformCapabilityAtAll() {
        PlatformCapabilitiesResponse capabilities =
                platformAssembler.assemble(CapabilityFixtures.memberOf(CapabilityFixtures.community()));

        assertFalse(capabilities.isCanCreateCommunity());
        assertFalse(capabilities.isCanListUsers());
        assertFalse(capabilities.isCanAdministerPlatform());
        assertFalse(capabilities.isCanCreateUsers());
    }

    /**
     * canAdministerPlatform has no guard, so the equivalence test cannot cover it, and asserting it
     * against PlatformAccessPolicy.canAdministerPlatform would only re-run the line the assembler
     * itself runs. This is its one anchor outside its own implementation, and it hard-codes today's
     * rule on purpose.
     *
     * <p>So: if canAdministerPlatform ever stops being the isPlatformAdmin flag, this assertion must
     * be <strong>changed deliberately</strong>, as part of deciding what the rule now is. It must
     * never be adjusted to whatever the code has started returning -- doing that would leave the
     * capability with nothing checking it at all.</p>
     */
    @Test
    void canAdministerPlatformIsTheIsPlatformAdminFlagAndNothingElse() {
        Community community = CapabilityFixtures.community();

        for (User caller : List.of(CapabilityFixtures.platformAdmin(),
                CapabilityFixtures.platformAdminMemberOf(community),
                CapabilityFixtures.adminOf(community),
                CapabilityFixtures.memberOf(community),
                CapabilityFixtures.disabledMemberOf(community),
                CapabilityFixtures.stranger())) {
            assertEquals(caller.isPlatformAdmin(),
                    platformAssembler.assemble(caller).isCanAdministerPlatform(),
                    "canAdministerPlatform must be the isPlatformAdmin flag");
        }
    }
}
