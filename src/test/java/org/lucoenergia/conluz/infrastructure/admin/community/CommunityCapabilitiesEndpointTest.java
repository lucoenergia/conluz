package org.lucoenergia.conluz.infrastructure.admin.community;

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
 * The community capabilities as a client actually receives them, over the listing and the detail
 * endpoint. The rules themselves are covered by the assembler's unit tests; what these add is that
 * the assembler is wired in at all, and that both the caller and the community reaching it are the
 * ones the client asked about — a controller passing the wrong principal, or a listing assembling
 * every row against the first community's id, would pass every test written a layer below.
 */
@Transactional
class CommunityCapabilitiesEndpointTest extends BaseControllerTest {

    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipService createMembershipService;

    @Test
    void theDetailEndpointReportsTheCallersCapabilities() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        String adminToken = loginAsCommunityAdmin(community.getId());

        mockMvc.perform(get("/api/v1/communities/{communityId}", community.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capabilities.canRead").value(true))
                .andExpect(jsonPath("$.capabilities.canManage").value(true))
                .andExpect(jsonPath("$.capabilities.canManageMemberships").value(true))
                .andExpect(jsonPath("$.capabilities.canCreatePlants").value(true))
                // Updating a community is platform-wide, and this caller only administers it.
                .andExpect(jsonPath("$.capabilities.canUpdate").value(false));
    }

    @Test
    void theDetailEndpointNarrowsTheCapabilitiesForAPlainMember() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        String memberToken = loginAsCommunityMember(community.getId());

        mockMvc.perform(get("/api/v1/communities/{communityId}", community.getId())
                        .header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capabilities.canRead").value(true))
                .andExpect(jsonPath("$.capabilities.canListPlants").value(true))
                .andExpect(jsonPath("$.capabilities.canReadProduction").value(true))
                .andExpect(jsonPath("$.capabilities.canManage").value(false))
                .andExpect(jsonPath("$.capabilities.canManageMemberships").value(false))
                .andExpect(jsonPath("$.capabilities.canCreatePlants").value(false));
    }

    /**
     * The listing case that a per-request assembly would get wrong: the caller administers one of
     * the two communities they belong to, so the two rows must not report the same capabilities.
     */
    @Test
    void theListingReportsCapabilitiesPerCommunityRatherThanPerRequest() throws Exception {
        Community administered = createCommunityRepository.create(CommunityMother.random().build());
        Community merelyJoined = createCommunityRepository.create(CommunityMother.random().build());
        String token = loginAsCommunityAdminOfAndMemberOf(administered, merelyJoined);

        mockMvc.perform(get("/api/v1/communities").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + administered.getId() + "')].capabilities.canManage")
                        .value(true))
                .andExpect(jsonPath("$[?(@.id == '" + merelyJoined.getId() + "')].capabilities.canManage")
                        .value(false))
                .andExpect(jsonPath("$[?(@.id == '" + merelyJoined.getId() + "')].capabilities.canRead")
                        .value(true));
    }

    private String loginAsCommunityAdminOfAndMemberOf(Community administered, Community merelyJoined)
            throws Exception {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        createMembershipService.create(administered.getId(), user.getId(), CommunityRole.COMMUNITY_ADMIN);
        createMembershipService.create(merelyJoined.getId(), user.getId(), CommunityRole.COMMUNITY_MEMBER);
        return loginUser(user);
    }
}
