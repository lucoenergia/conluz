package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin;

import org.junit.jupiter.api.Assertions;
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
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;
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
        User admin = UserMother.randomUser();
        admin.setEnabled(true);
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
    void testRevokeLastPlatformAdminIsRejected() throws Exception {

        // Only the default platform admin exists (count == 1). Revoking any user is rejected with 409,
        // because the system can never be left with zero platform admins.
        String authHeader = loginAsDefaultPlatformAdmin();

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
                .andExpect(jsonPath("$.errors[0].code").value("USER_LAST_PLATFORM_ADMIN"))
                .andExpect(jsonPath("$.errors[0].params").value(nullValue()))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());

        // The default platform admin still holds the flag.
        Assertions.assertTrue(getUserRepository.findByPersonalId(UserPersonalId.of(PERSONAL_ID))
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
    void testWithUnknownUser() throws Exception {

        // A second platform admin keeps the count above one, so the last-admin rail passes and the
        // unknown target id surfaces as a 404 rather than a 409.
        String authHeader = loginAsDefaultPlatformAdmin();
        createPlatformAdmin();

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
