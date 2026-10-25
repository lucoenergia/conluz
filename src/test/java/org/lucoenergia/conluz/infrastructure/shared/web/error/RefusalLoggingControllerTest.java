package org.lucoenergia.conluz.infrastructure.shared.web.error;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.register.BaseRegisterMemberTest;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A refused request is logged once, without a stack trace, at the level of what the refusal means: INFO for the
 * request's data or a business rule, WARN for a security refusal (#479). The base class checks that no log line
 * contains a personal id, an email address, a password or a token the test used.
 */
class RefusalLoggingControllerTest extends BaseRegisterMemberTest {

    @Test
    void aBodyFailingValidation_isOneInfoLine() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        Map<String, Object> body = record();
        body.remove("fullName");

        assertLoggedOnce(Level.INFO, () -> register(bearer, community.getId(), body)
                .andExpect(status().isBadRequest()));
    }

    @Test
    void aBusinessConflict_isOneInfoLine() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        Map<String, Object> body = record();
        register(bearer, community.getId(), body).andExpect(status().isOk());

        assertLoggedOnce(Level.INFO, () -> register(bearer, community.getId(), body)
                .andExpect(status().isConflict()));
    }

    @Test
    void aResourceNotFound_isOneInfoLine() throws Exception {
        String bearer = loginAsAdminOf(newCommunity());

        assertLoggedOnce(Level.INFO, () -> mockMvc.perform(get("/api/v1/plants/{plantId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound()));
    }

    @Test
    void aRequestWithoutToken_isOneWarning() throws Exception {
        assertLoggedOnce(Level.WARN, () -> mockMvc.perform(get("/api/v1/plants/{plantId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized()));
    }

    @Test
    void aForbiddenRequest_isOneWarning() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsMemberOf(community);

        assertLoggedOnce(Level.WARN, () -> register(bearer, community.getId(), record())
                .andExpect(status().isForbidden()));
    }

    @Test
    void anInvalidEmail_isRefusedNamingTheField_withoutRepeatingTheValue() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        Map<String, Object> body = record();
        String invalidEmail = "lucia.perez.at.contact.test";
        remember(invalidEmail);
        body.put("contactEmail", invalidEmail);

        String response = register(bearer, community.getId(), body)
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).contains("contactEmail");
        assertNoSecretIn(response);
    }

    @Test
    void anInvalidPersonalId_isRefused_withoutRepeatingTheValue() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        Map<String, Object> body = record();
        String valid = (String) body.get("personalId");
        char letter = valid.charAt(valid.length() - 1);
        // Any other control letter makes the DNI invalid
        String invalidPersonalId = valid.substring(0, valid.length() - 1) + (letter == 'A' ? 'B' : 'A');
        remember(invalidPersonalId);
        body.put("personalId", invalidPersonalId);

        String response = register(bearer, community.getId(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("USER_PERSONAL_ID_INVALID"))
                .andReturn().getResponse().getContentAsString();

        assertNoSecretIn(response);
    }

    @Test
    void aLoginWithoutPassword_isAnInvalidBody_notAFailedLogin() throws Exception {
        User user = newUser();
        remember(user.getPersonalId());
        String body = objectMapper.writeValueAsString(Map.of("username", user.getPersonalId()));

        try (LogCapture login = LogCapture.ofRoot()) {
            assertLoggedOnce(Level.INFO, () -> mockMvc.perform(post("/api/v1/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400)));

            assertThat(login.allOfThisThread())
                    .noneMatch(event -> event.getFormattedMessage().startsWith("Failed login"));
        }
    }

    /**
     * A registration body whose personal id is a secret too.
     */
    private Map<String, Object> record() {
        Map<String, Object> body = newRecord();
        remember((String) body.get("personalId"));
        return body;
    }

    /**
     * Runs the request and asserts that the refusal is one line of {@link ErrorBuilder}, at {@code level}, without a
     * stack trace, and that nothing else on the request's thread logs at WARN or above.
     */
    private static void assertLoggedOnce(Level level, Request request) throws Exception {
        try (LogCapture refusal = LogCapture.ofRoot()) {
            request.perform();

            List<ILoggingEvent> events = refusal.allOfThisThread();
            assertThat(events)
                    .filteredOn(event -> event.getLoggerName().equals(ErrorBuilder.class.getName()))
                    .singleElement()
                    .satisfies(event -> {
                        assertThat(event.getLevel()).isEqualTo(level);
                        assertThat(event.getThrowableProxy()).isNull();
                    });
            assertThat(events)
                    .filteredOn(event -> !event.getLoggerName().equals(ErrorBuilder.class.getName()))
                    .noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.WARN));
        }
    }

    @FunctionalInterface
    private interface Request {
        ResultActions perform() throws Exception;
    }
}
