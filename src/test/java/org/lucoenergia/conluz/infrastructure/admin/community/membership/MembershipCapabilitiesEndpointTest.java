package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The membership capabilities as a client receives them.
 *
 * <p>The roster listing is community-admin-only, so a plain member never sees one of these at all.
 * What the capabilities distinguish is therefore the <em>platform</em> admin from the
 * <em>community</em> admin: both may administer the roster, but an investment is the member's own
 * money and payback is their own figure, so administering the platform confers neither.</p>
 */
@Transactional
class MembershipCapabilitiesEndpointTest extends BaseControllerTest {

    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipService createMembershipService;

    @Test
    void aCommunityAdminIsOfferedEverythingOnTheRoster() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        persistMemberOf(community);

        mockMvc.perform(get("/api/v1/communities/{communityId}/memberships", community.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].capabilities.canUpdateRole").value(true))
                .andExpect(jsonPath("$[0].capabilities.canDelete").value(true))
                .andExpect(jsonPath("$[0].capabilities.canManageInvestment").value(true))
                .andExpect(jsonPath("$[0].capabilities.canReadPayback").value(true));
    }

    /**
     * The asymmetry worth reporting: a platform admin may administer this roster without being able
     * to touch its members' money. No role name conveys that, and the rules behind the two are
     * genuinely different -- canManageMemberships has a platform-admin branch, canManageInvestment
     * and canReadPayback deliberately do not.
     */
    @Test
    void aNonMemberPlatformAdminMayAdministerTheRosterButNotTheMoney() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        persistMemberOf(community);

        mockMvc.perform(get("/api/v1/communities/{communityId}/memberships", community.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginAsDefaultPlatformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].capabilities.canUpdateRole").value(true))
                .andExpect(jsonPath("$[0].capabilities.canDelete").value(true))
                .andExpect(jsonPath("$[0].capabilities.canManageInvestment").value(false))
                .andExpect(jsonPath("$[0].capabilities.canReadPayback").value(false));
    }

    /**
     * Every row on the roster is assembled against its own membership, so the admin's own row --
     * where canReadPayback is true through the self branch rather than the admin one -- must not be
     * confused with anybody else's.
     */
    @Test
    void everyRowCarriesItsOwnCapabilities() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        persistMemberOf(community);
        persistMemberOf(community);

        mockMvc.perform(get("/api/v1/communities/{communityId}/memberships", community.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].capabilities").exists())
                .andExpect(jsonPath("$[1].capabilities").exists())
                .andExpect(jsonPath("$[2].capabilities").exists());
    }

    private User persistMemberOf(Community community) {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        createMembershipService.create(community.getId(), user.getId(), CommunityRole.COMMUNITY_MEMBER);
        return user;
    }
}
