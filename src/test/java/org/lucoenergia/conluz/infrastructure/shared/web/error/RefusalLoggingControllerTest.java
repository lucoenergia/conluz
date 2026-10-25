package org.lucoenergia.conluz.infrastructure.shared.web.error;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.register.BaseRegisterMemberTest;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
