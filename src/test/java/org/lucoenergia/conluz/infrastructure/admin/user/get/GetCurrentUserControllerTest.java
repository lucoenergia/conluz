package org.lucoenergia.conluz.infrastructure.admin.user.get;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import static org.lucoenergia.conluz.domain.admin.community.CommunityMother.random;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetCurrentUserControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/users/current";

    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipService createMembershipService;

    @Test
    void testGetCurrentUserSuccess() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalId").value(DefaultUserAdminMother.PERSONAL_ID))
                .andExpect(jsonPath("$.fullName").value(DefaultUserAdminMother.FULL_NAME))
                .andExpect(jsonPath("$.email").value(DefaultUserAdminMother.EMAIL))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.isPlatformAdmin").value(true))
                .andExpect(jsonPath("$.memberships").isEmpty())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void testGetCurrentUserReturnsMembershipsForCommunityAdmin() throws Exception {
        loginAsDefaultPlatformAdmin();

        Community community = createCommunityRepository.create(random().build());

        User communityAdmin = UserMother.randomUser();
        communityAdmin.enable();
        createUserRepository.create(communityAdmin);
        createMembershipService.create(community.getId(), communityAdmin.getId(), CommunityRole.COMMUNITY_ADMIN);

        String authHeader = loginUser(communityAdmin);

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPlatformAdmin").value(false))
                .andExpect(jsonPath("$.memberships").isNotEmpty())
                .andExpect(jsonPath("$.memberships.['" + community.getId().toString() + "']").value(CommunityRole.COMMUNITY_ADMIN.name()));
    }

    @Test
    void testGetCurrentUserUnauthorizedWhenMissingToken() throws Exception {
        mockMvc.perform(get(URL))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
