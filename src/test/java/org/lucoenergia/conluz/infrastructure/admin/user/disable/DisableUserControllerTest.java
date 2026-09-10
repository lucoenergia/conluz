package org.lucoenergia.conluz.infrastructure.admin.user.disable;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother.PERSONAL_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class DisableUserControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private GetUserRepository getUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipService createMembershipService;

    @Test
    void testDisableUser() throws Exception {

        // Create a user enabled
        User user = UserMother.randomUser();
        user.setEnabled(true);
        createUserRepository.create(user);
        Assertions.assertTrue(getUserRepository.existsByPersonalId(UserPersonalId.of(user.getPersonalId())));

        // Login as default admin user
        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(post(String.format("/api/v1/users/%s/disable", user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        Assertions.assertFalse(getUserRepository.findByPersonalId(UserPersonalId.of(user.getPersonalId())).get().isEnabled());
    }

    @Test
    void testWithUnknownUser() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

        final String userId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/users/" + userId + "/disable")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithoutToken() throws Exception {

        final String userId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/users/" + userId + "/disable")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testAuthenticatedUserWithoutAdminRoleCannotAccess() throws Exception {

        String authHeader = loginAsPartner();

        final String userId = UUID.randomUUID().toString();

        // Test users endpoint
        mockMvc.perform(post("/api/v1/users/" + userId + "/disable")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    void testDisableLastPlatformAdminIsRejected() throws Exception {

        // Initialize the default platform admin.
        init();

        // Create a community and add the default platform admin as a member.
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User defaultAdmin = getUserRepository.findByPersonalId(UserPersonalId.of(PERSONAL_ID)).get();
        createMembershipService.create(community.getId(), defaultAdmin.getId(), CommunityRole.COMMUNITY_MEMBER);

        // Create a community admin of that community (not a platform admin) and log in.
        String authHeader = loginAsCommunityAdmin(community.getId());

        // The default admin is the only platform admin (count == 1). Disabling them
        // is rejected with 409, because the system can never be left with zero enabled platform admins.
        mockMvc.perform(post(String.format("/api/v1/users/%s/disable", defaultAdmin.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(HttpStatus.CONFLICT.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].code").value("USER_LAST_PLATFORM_ADMIN"))
                .andExpect(jsonPath("$.errors[0].params").value(nullValue()))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());

        // The default admin is still enabled.
        Assertions.assertTrue(getUserRepository.findByPersonalId(UserPersonalId.of(PERSONAL_ID))
                .get().isEnabled());
    }
}
