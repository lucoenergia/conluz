package org.lucoenergia.conluz.infrastructure.admin.user.logout;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.security.auth.AuthParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class LogoutUserControllerTest extends BaseControllerTest {

    private static final String LOGOUT_PATH = "/api/v1/logout";

    @Test
    void testLogoutWithToken() throws Exception {
        // Arrange - Login to get a token
        String authToken = loginAsDefaultAdmin();

        // Act & Assert - Logout with the token
        mockMvc.perform(post(LOGOUT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", authToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(AuthParameter.ACCESS_TOKEN.getCookieName(), 0)); // Cookie should be removed
    }

    @Test
    void testLogoutWithoutToken() throws Exception {

        // Act & Assert - Logout with the token
        mockMvc.perform(post(LOGOUT_PATH)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testLogoutWithTokenTwice() throws Exception {
        // Arrange - Login to get a token
        String authToken = loginAsDefaultAdmin();

        // Act & Assert - Logout with the token
        mockMvc.perform(post(LOGOUT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", authToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(AuthParameter.ACCESS_TOKEN.getCookieName(), 0)); // Cookie should be removed

        // Try to logout again with the same token (should not work since the token has been included in the blacklist)
        mockMvc.perform(post(LOGOUT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", authToken))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
