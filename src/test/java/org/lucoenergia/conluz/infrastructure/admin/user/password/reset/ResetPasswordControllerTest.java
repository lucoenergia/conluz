package org.lucoenergia.conluz.infrastructure.admin.user.password.reset;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.AuthenticationThrottleServiceImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResetPasswordControllerTest extends BasePasswordResetTest {

    private static final String INVALID_TOKEN = "USER_PASSWORD_RESET_TOKEN_INVALID";

    @Test
    void aValidReset_replacesThePassword_endsEverySession_clearsTheFlag_andUsesUpTheToken() throws Exception {
        User user = newUser(User::requirePasswordChange);
        String oldPassword = user.getPassword();
        String firstSession = loginUser(user);
        String secondSession = loginUser(user);
        String token = issueDirectly(user);
        String newPassword = newPassword();
        assertThat(userRow(user).get("must_change_password")).isEqualTo(true);
        // Sessions opened in the same second as the reset are not told apart from it
        awaitTheNextSecond();

        reset(token, newPassword).andExpect(status().isNoContent());

        login(user.getPersonalId(), oldPassword).andExpect(status().isUnauthorized());
        login(user.getPersonalId(), newPassword).andExpect(status().isOk());
        for (String session : List.of(firstSession, secondSession)) {
            mockMvc.perform(get("/api/v1/users/current").header(HttpHeaders.AUTHORIZATION, session))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(userRow(user).get("must_change_password")).isEqualTo(false);
        reset(token, newPassword()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value(INVALID_TOKEN));
    }

    @Test
    void aSuccessfulReset_answersNoBody_noCookie_andNoSession() throws Exception {
        User user = newUser();
        String token = issueDirectly(user);

        MvcResult result = reset(token, newPassword()).andExpect(status().isNoContent()).andReturn();

        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(result.getResponse().getCookies()).isEmpty();
        assertThat(result.getResponse().getHeader(HttpHeaders.AUTHORIZATION)).isNull();
    }

    @Test
    void everyUnusableToken_isAnsweredTheSame400_andLoggedOnceWithoutTheToken() throws Exception {
        Map<String, String> unusable = new LinkedHashMap<>();

        User used = newUser();
        String usedToken = issueDirectly(used);
        reset(usedToken, newPassword()).andExpect(status().isNoContent());
        unusable.put("used", usedToken);

        User reissued = newUser();
        unusable.put("revoked", issueDirectly(reissued));
        issueDirectly(reissued);

        User disabledLater = newUser();
        unusable.put("user disabled after issuing", issueDirectly(disabledLater));
        jdbcTemplate.update("UPDATE users SET enabled = false WHERE id = ?", disabledLater.getId());

        unusable.put("unknown", unknownToken());
        unusable.put("malformed", "%%%not a token%%%");
        unusable.put("one character", "x");
        unusable.put("very long", "a".repeat(500));
        unusable.put("empty", "");

        ObjectNode expected = null;
        for (Map.Entry<String, String> entry : unusable.entrySet()) {
            ObjectNode body = expectInvalidToken(entry.getKey(), entry.getValue());
            if (expected == null) {
                expected = body;
            }
            assertThat(body).as(entry.getKey()).isEqualTo(expected);
        }

        String expiring = issueDirectly(newUser());
        clock.advance(PURPOSE.lifetime());
        assertThat(expectInvalidToken("expired", expiring)).isEqualTo(expected);
    }

    private ObjectNode expectInvalidToken(String name, String token) throws Exception {
        logs.clear();

        MvcResult result = reset(token, newPassword())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value(INVALID_TOKEN))
                .andReturn();

        assertNoSecretIn(result.getResponse().getContentAsString());
        List<ILoggingEvent> warnings = logs.warningsAndAboveOfThisThread();
        assertThat(warnings).as(name).hasSize(1);
        assertThat(warnings.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(warnings.get(0).getFormattedMessage())
                .isEqualTo("Failed password reset: ip=127.0.0.1, reason=INVALID_TOKEN");
        return comparable(result);
    }

    @Test
    void aNewPasswordBreakingThePolicy_orEqualToTheCurrentOne_changesNothing_andLeavesTheTokenUsable()
            throws Exception {
        User user = newUser();
        String token = issueDirectly(user);
        Map<String, Object> before = userRow(user);

        reset(token, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("USER_PASSWORD_POLICY_VIOLATION"))
                .andExpect(jsonPath("$.errors[0].params.rule").value("TOO_SHORT"));
        assertThat(userRow(user)).isEqualTo(before);

        reset(token, user.getPassword())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("USER_PASSWORD_UNCHANGED"));
        assertThat(userRow(user)).isEqualTo(before);
        // The token is never even consumed: a rollback would undo that, but only while the consumption shares the
        // reset's transaction
        verify(consumeOneTimeTokenService, never()).consume(any(), any());
        // Neither refusal counts against the client address
        assertThat(logs.allOfThisThread())
                .noneMatch(event -> event.getLoggerName().equals(AuthenticationThrottleServiceImpl.class.getName()));

        reset(token, newPassword()).andExpect(status().isNoContent());
        assertThat(userRow(user)).isNotEqualTo(before);
    }

    @Test
    void aUserThrottledForFailedLogins_canLogInAtOnceAfterAReset() throws Exception {
        User user = newUser();
        for (int i = 0; i < 5; i++) {
            login(user.getPersonalId(), "a wrong password, " + i).andExpect(status().isUnauthorized());
        }
        login(user.getPersonalId(), user.getPassword()).andExpect(status().isTooManyRequests());
        String token = issueDirectly(user);
        String newPassword = newPassword();

        reset(token, newPassword).andExpect(status().isNoContent());

        login(user.getPersonalId(), newPassword).andExpect(status().isOk());
    }

    @Test
    void invalidTokensFromOneClientAddress_areThrottled_withRetryAfter() throws Exception {
        String clientIp = "203.0.113.51";
        for (int i = 0; i < 20; i++) {
            reset(unknownToken(), newPassword(), clientIp).andExpect(status().isBadRequest());
        }

        reset(unknownToken(), newPassword(), clientIp)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.errors[0].code").value("AUTH_TOO_MANY_FAILED_ATTEMPTS"));
        // Even a valid token waits: the client address is checked first
        User user = newUser();
        String token = issueDirectly(user);
        reset(token, newPassword(), clientIp).andExpect(status().isTooManyRequests());
        reset(token, newPassword(), "203.0.113.52").andExpect(status().isNoContent());
    }

    @Test
    void anyTokenPresented_isIgnored_evenThatOfAUserWhoMustChangeTheirPassword() throws Exception {
        User flagged = newUser(User::requirePasswordChange);
        String flaggedSession = loginUser(flagged);

        for (String authorization : List.of(flaggedSession, "Bearer not-a-jwt")) {
            String token = issueDirectly(flagged);
            mockMvc.perform(resetting(token, newPassword(), "127.0.0.1")
                            .header(HttpHeaders.AUTHORIZATION, authorization))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(resetting(unknownToken(), newPassword(), "127.0.0.1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value(INVALID_TOKEN));
    }

    @Test
    void aMissingTokenOrPassword_isABadRequest() throws Exception {
        for (String body : List.of("{}", "{\"token\":\"x\"}", "{\"newPassword\":\"a password of 15+ chars\"}")) {
            mockMvc.perform(post(RESET_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void theWholeRecovery_leavesNoSecretInAnyResponseOrLogLine() throws Exception {
        User user = newUser();
        MvcResult requested = requestRecovery(user.getPersonalId()).andReturn();
        String token = tokenFrom(awaitEmails(1)[0]);

        List<MvcResult> results = List.of(
                requested,
                reset(unknownToken(), newPassword()).andReturn(),
                reset(token, "short").andReturn(),
                reset(token, user.getPassword()).andReturn(),
                reset(token, newPassword()).andReturn(),
                reset(token, newPassword()).andReturn());

        for (MvcResult result : results) {
            assertNoSecretIn(result.getResponse().getContentAsString());
            if (result.getResolvedException() != null) {
                assertNoSecretIn(String.valueOf(result.getResolvedException().getMessage()));
                assertNoSecretIn(String.valueOf(result.getResolvedException()));
            }
        }
        // The log lines are checked after every test
    }

    private ResultActions login(String personalId, String password) throws Exception {
        remember(password);
        return mockMvc.perform(post("/api/v1/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("username", personalId, "password", password))));
    }

    private String unknownToken() {
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        remember(token);
        return token;
    }

    /**
     * The body without the fields that differ on every response.
     */
    private ObjectNode comparable(MvcResult result) throws Exception {
        ObjectNode body = (ObjectNode) objectMapper.readTree(result.getResponse().getContentAsString());
        body.remove("timestamp");
        body.remove("traceId");
        return body;
    }
}
