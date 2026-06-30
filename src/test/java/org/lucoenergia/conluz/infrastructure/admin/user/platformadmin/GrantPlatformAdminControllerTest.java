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

import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GrantPlatformAdminControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private GetUserRepository getUserRepository;

    @Test
    void testGrantPlatformAdmin() throws Exception {

        // Create a regular (non platform admin) user
        User user = UserMother.randomUser();
        user.setEnabled(true);
        user.setPlatformAdmin(false);
        createUserRepository.create(user);

        // Login as default platform admin
        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(post(String.format("/api/v1/users/%s/grant-platform-admin", user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        Assertions.assertTrue(getUserRepository.findByPersonalId(UserPersonalId.of(user.getPersonalId()))
                .get().isPlatformAdmin());
    }

    @Test
    void testWithUnknownUser() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

        final String userId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/users/" + userId + "/grant-platform-admin")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    void testWithoutToken() throws Exception {

        final String userId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/users/" + userId + "/grant-platform-admin")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void testCommunityAdminCannotAccess() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        final String userId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/users/" + userId + "/grant-platform-admin")
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

        mockMvc.perform(post("/api/v1/users/" + userId + "/grant-platform-admin")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()));
    }
}
