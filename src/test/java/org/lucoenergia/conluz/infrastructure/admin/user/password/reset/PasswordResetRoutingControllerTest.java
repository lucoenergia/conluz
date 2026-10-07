package org.lucoenergia.conluz.infrastructure.admin.user.password.reset;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.method.HandlerMethod;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The two password recovery paths sit under {@code /api/v1/users}, next to endpoints that take a user id in the same
 * position. Each reaches its own handler, and making them public opened nothing else under that path.
 */
class PasswordResetRoutingControllerTest extends BasePasswordResetTest {

    @Test
    void theRecoveryPath_reachesItsOwnHandler_withoutAuthentication() throws Exception {
        MvcResult result = requestRecovery("UNKNOWNID9").andExpect(status().isAccepted()).andReturn();

        assertThat(handlerOf(result)).isEqualTo("RequestPasswordResetController#requestPasswordReset");
    }

    @Test
    void theResetPath_reachesItsOwnHandler_withoutAuthentication() throws Exception {
        String unknown = UUID.randomUUID().toString();
        remember(unknown);

        MvcResult result = reset(unknown, newPassword()).andExpect(status().isBadRequest()).andReturn();

        assertThat(handlerOf(result)).isEqualTo("ResetPasswordController#resetPassword");
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/v1/users",
            "POST, /api/v1/users",
            "GET, /api/v1/users/current",
            "PUT, /api/v1/users/current/password",
            "PUT, /api/v1/users/profile",
            "GET, /api/v1/users/{userId}",
            "PUT, /api/v1/users/{userId}",
            "DELETE, /api/v1/users/{userId}",
            "GET, /api/v1/users/{userId}/supplies",
            "POST, /api/v1/users/{userId}/enable",
            "POST, /api/v1/users/{userId}/disable",
            "POST, /api/v1/users/{userId}/grant-platform-admin",
            "POST, /api/v1/users/{userId}/revoke-platform-admin",
            // The recovery paths are public on their own method only, and nothing next to them is
            "GET, /api/v1/users/password/recover",
            "PUT, /api/v1/users/password/reset",
            "GET, /api/v1/users/password",
            "POST, /api/v1/users/password",
            "POST, /api/v1/users/password/other",
            "POST, /api/v1/users/password/reset/more"
    })
    void everyOtherEndpointUnderUsers_stillRequiresAuthentication(String method, String path) throws Exception {
        String uri = path.replace("{userId}", UUID.randomUUID().toString());

        mockMvc.perform(request(HttpMethod.valueOf(method), uri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private static String handlerOf(MvcResult result) {
        HandlerMethod handler = (HandlerMethod) result.getHandler();
        assertThat(handler).isNotNull();
        return handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName();
    }
}
