package org.lucoenergia.conluz.infrastructure.admin.user.delete;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
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
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother.PERSONAL_ID;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class DeleteUserControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private GetUserRepository getUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipService createMembershipService;
    @Autowired
    private ManagePlatformAdminRepository managePlatformAdminRepository;

    @Test
    void testDeleteUser() throws Exception {

        // Create a user
        User user = UserMother.randomUser();
        createUserRepository.create(user);
        Assertions.assertTrue(getUserRepository.existsByPersonalId(UserPersonalId.of(user.getPersonalId())));

        // Login as default admin
        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(delete(String.format("/api/v1/users/%s", user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        Assertions.assertFalse(getUserRepository.existsByPersonalId(UserPersonalId.of(user.getPersonalId())));
    }

    @Test
    void testWithUnknownUser() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

        final String userId = UUID.randomUUID().toString();

        mockMvc.perform(delete("/api/v1/users/" + userId)
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
    void
    testWithoutIdInPath() throws Exception {
        final String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(delete("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithoutToken() throws Exception {

        mockMvc.perform(delete("/api/v1/users")
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

        mockMvc.perform(delete(String.format("/api/v1/users/%s", userId))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    @DisplayName("ACC-008 a community admin deleting a member of their community is refused, and the account is kept")
    void aCommunityAdminDeletingAMemberIsForbidden() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User member = UserMother.randomUser();
        member.setEnabled(true);
        createUserRepository.create(member);
        createMembershipService.create(community.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);

        mockMvc.perform(delete(String.format("/api/v1/users/%s", member.getId()))
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.getId()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        User stored = getUserRepository.findById(UserId.of(member.getId())).orElseThrow();
        Assertions.assertTrue(stored.isEnabled());
    }

    @Test
    @DisplayName("ACC-008 a community admin deleting a platform admin who is a member of their community is refused")
    void aCommunityAdminDeletingThePlatformAdminOfTheirCommunityIsForbidden() throws Exception {

        // Initialize the default platform admin.
        init();

        // Create a community and add the default platform admin as a member.
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User defaultAdmin = getUserRepository.findByPersonalId(UserPersonalId.of(PERSONAL_ID)).get();
        createMembershipService.create(community.getId(), defaultAdmin.getId(), CommunityRole.COMMUNITY_MEMBER);

        // Create a community admin of that community (not a platform admin) and log in.
        String authHeader = loginAsCommunityAdmin(community.getId());

        // Deleting the account would remove them from every community: not a community admin's to do.
        mockMvc.perform(delete(String.format("/api/v1/users/%s", defaultAdmin.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()));

        // The default admin still exists, still enabled.
        Assertions.assertTrue(getUserRepository.findByPersonalId(UserPersonalId.of(PERSONAL_ID)).get().isEnabled());
    }

    /**
     * Only a platform admin may delete another user, and they count as an enabled platform admin themselves, so the
     * last one can only be "left" when the target is already disabled: that is the one path to the 409 left.
     */
    @Test
    void testDeleteLastPlatformAdminIsRejected() throws Exception {

        // The default platform admin is the only enabled one; the target is a disabled platform admin.
        String authHeader = loginAsDefaultPlatformAdmin();
        User disabledAdmin = UserMother.randomUser();
        disabledAdmin.setEnabled(false);
        createUserRepository.create(disabledAdmin);
        managePlatformAdminRepository.grant(UserId.of(disabledAdmin.getId()));

        mockMvc.perform(delete(String.format("/api/v1/users/%s", disabledAdmin.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(HttpStatus.CONFLICT.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].code").value("USER_LAST_PLATFORM_ADMIN"))
                .andExpect(jsonPath("$.errors[0].params").value(nullValue()))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());

        // The target still exists.
        Assertions.assertTrue(getUserRepository.findById(UserId.of(disabledAdmin.getId())).isPresent());
    }

    @Test
    void testCannotActOnOwnAccount() throws Exception {
        // Nobody may delete themselves, platform admin included: the guard settles visibility
        // first and then refuses because the target is the caller -- a 403, not a 404.
        String authHeader = loginAsDefaultPlatformAdmin();

        User self = getUserRepository.findByPersonalId(UserPersonalId.of(PERSONAL_ID)).get();

        mockMvc.perform(delete(String.format("/api/v1/users/%s", self.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()));

        // The account is still there.
        Assertions.assertTrue(getUserRepository.findByPersonalId(UserPersonalId.of(PERSONAL_ID)).isPresent());
    }
}
