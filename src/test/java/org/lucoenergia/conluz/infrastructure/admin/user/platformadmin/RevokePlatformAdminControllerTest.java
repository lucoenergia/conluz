package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import static org.lucoenergia.conluz.domain.admin.community.CommunityMother.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class RevokePlatformAdminControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private GetUserRepository getUserRepository;

    /**
     * Creates and persists an enabled platform-admin user.
     */
    private User createPlatformAdmin() {
        return createPlatformAdmin(true);
    }

    private User createPlatformAdmin(boolean enabled) {
        User admin = UserMother.randomUser();
        admin.setEnabled(enabled);
        admin.setPlatformAdmin(true);
        createUserRepository.create(admin);
        return admin;
    }

    @Test
    void testRevokePlatformAdminWhenAnotherAdminExists() throws Exception {

        // Default platform admin is the first admin; create a second one to revoke.
        String authHeader = loginAsDefaultPlatformAdmin();
        User secondAdmin = createPlatformAdmin();

        mockMvc.perform(post(String.format("/api/v1/users/%s/revoke-platform-admin", secondAdmin.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        Assertions.assertFalse(getUserRepository.findByPersonalId(UserPersonalId.of(secondAdmin.getPersonalId()))
                .get().isPlatformAdmin());
    }

    @Test
    @DisplayName("ACC-013 revoking a user who is not a platform admin is refused, and nothing changes")
    void testRevokeUserWhoIsNotAPlatformAdminIsRejected() throws Exception {

        // A second enabled platform admin, so the last-admin rail could not be what refuses it
        String authHeader = loginAsDefaultPlatformAdmin();
        createPlatformAdmin();

        User target = UserMother.randomUser();
        target.setEnabled(true);
        target.setPlatformAdmin(false);
        createUserRepository.create(target);

        mockMvc.perform(post(String.format("/api/v1/users/%s/revoke-platform-admin", target.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(HttpStatus.CONFLICT.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].code").value("USER_NOT_PLATFORM_ADMIN"))
                .andExpect(jsonPath("$.errors[0].params").value(nullValue()))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());

        // Nothing changed: the target still lacks the flag and the default platform admin still holds it
        Assertions.assertFalse(getUserRepository.findByPersonalId(UserPersonalId.of(target.getPersonalId()))
                .get().isPlatformAdmin());
        Assertions.assertTrue(getUserRepository.findByPersonalId(UserPersonalId.of(PERSONAL_ID))
                .get().isPlatformAdmin());
    }

    @Test
    @DisplayName("ACC-013 revoking a disabled platform admin is allowed while only one platform admin is enabled")
    void testRevokeDisabledPlatformAdminWithOneEnabledAdmin() throws Exception {

        // The caller is the only enabled platform admin
        String authHeader = loginAsDefaultPlatformAdmin();
        User disabledAdmin = createPlatformAdmin(false);

        mockMvc.perform(post(String.format("/api/v1/users/%s/revoke-platform-admin", disabledAdmin.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        Assertions.assertFalse(getUserRepository.findByPersonalId(UserPersonalId.of(disabledAdmin.getPersonalId()))
                .get().isPlatformAdmin());
    }

    @Test
    void testRevokeOwnFlagIsRejected() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

        User self = getUserRepository.findByPersonalId(UserPersonalId.of(PERSONAL_ID)).get();

        mockMvc.perform(post(String.format("/api/v1/users/%s/revoke-platform-admin", self.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()));

        // The flag is unchanged.
        Assertions.assertTrue(getUserRepository.findByPersonalId(UserPersonalId.of(PERSONAL_ID))
                .get().isPlatformAdmin());
    }

    @Test
    @DisplayName("ACC-013 revoking an unknown user answers 404, whatever the number of enabled platform admins")
    void testWithUnknownUser() throws Exception {

        // Only the default platform admin exists: the target is checked before the last-admin rail
        String authHeader = loginAsDefaultPlatformAdmin();

        final String userId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/users/" + userId + "/revoke-platform-admin")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                // Backward compatibility: an unmigrated error still carries a matching scalar
                // `message` and a null `code`, proving the additive contract doesn't break it.
                .andExpect(jsonPath("$.errors[0].code").value(nullValue()))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    void testWithoutToken() throws Exception {

        final String userId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/users/" + userId + "/revoke-platform-admin")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void testCommunityAdminCannotAccess() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        final String userId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/users/" + userId + "/revoke-platform-admin")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()));
    }

    @Test
    void testRegularMemberCannotAccess() throws Exception {

        String authHeader = loginAsPartner();

        final String userId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/users/" + userId + "/revoke-platform-admin")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()));
    }
}
