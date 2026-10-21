package org.lucoenergia.conluz.infrastructure.admin.user.enable;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.enable.EnableUserService;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother.PERSONAL_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class EnableUserControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private GetUserRepository getUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private EnableUserService enableUserService;

    @Test
    @DisplayName("ACC-008 a community admin enabling a member of their community is refused, and the account is unchanged")
    void aCommunityAdminEnablingAMemberIsForbidden() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User member = UserMother.randomUser();
        member.setEnabled(false);
        createUserRepository.create(member);
        createMembershipRepository.create(community.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);

        mockMvc.perform(post(String.format("/api/v1/users/%s/enable", member.getId()))
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.getId()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        Assertions.assertFalse(getUserRepository.findById(UserId.of(member.getId())).orElseThrow().isEnabled());
    }

    @Test
    @DisplayName("ACC-020 a platform admin enabling a user is told the user is missing, and the account stays disabled")
    void aPlatformAdminEnablingAUserIsToldTheUserIsMissing() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User user = UserMother.randomUser();
        user.setEnabled(false);
        createUserRepository.create(user);
        createMembershipRepository.create(community.getId(), user.getId(), CommunityRole.COMMUNITY_MEMBER);

        mockMvc.perform(post(String.format("/api/v1/users/%s/enable", user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, loginAsDefaultPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        Assertions.assertFalse(getUserRepository.findByPersonalId(UserPersonalId.of(user.getPersonalId())).get().isEnabled());
    }

    /**
     * No caller may enable another user over HTTP (ACC-020), so what enabling does is pinned on the service.
     */
    @Test
    void enablingAUserThroughTheServiceEnablesTheAccount() {
        User user = UserMother.randomUser();
        user.setEnabled(false);
        createUserRepository.create(user);

        enableUserService.enable(UserId.of(user.getId()));

        Assertions.assertTrue(getUserRepository.findByPersonalId(UserPersonalId.of(user.getPersonalId())).get().isEnabled());
    }

    @Test
    void testWithUnknownUser() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

        final String userId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/users/" + userId + "/enable")
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

        mockMvc.perform(post("/api/v1/users/" + userId + "/enable")
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

        final String userId = UUID.randomUUID().toString();

        String authHeader = loginAsPartner();

        // Test users endpoint
        mockMvc.perform(post("/api/v1/users/" + userId + "/enable")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }
    @Test
    void testCannotActOnOwnAccount() throws Exception {
        // Nobody may enable themselves, platform admin included: the guard settles the edit
        // decision first and then refuses because the target is the caller -- a 403, not a 404.
        String authHeader = loginAsDefaultPlatformAdmin();

        User self = getUserRepository.findByPersonalId(UserPersonalId.of(PERSONAL_ID)).get();

        mockMvc.perform(post(String.format("/api/v1/users/%s/enable", self.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()));
    }
}
