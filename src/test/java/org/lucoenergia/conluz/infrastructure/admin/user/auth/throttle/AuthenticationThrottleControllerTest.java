package org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Throttling of failed attempts on login and password change, through the HTTP API (#332).
 */
@Transactional
class AuthenticationThrottleControllerTest extends BaseControllerTest {

    private static final String LOGIN_URL = "/api/v1/login";
    private static final String CHANGE_PASSWORD_URL = "/api/v1/users/current/password";
    private static final String CURRENT_USER_URL = "/api/v1/users/current";
    private static final String WRONG_PASSWORD = "not the password of this user";
    private static final String NEW_PASSWORD = "a brand new password for this user";
    private static final String IP = "203.0.113.7";
    private static final String OTHER_IP = "198.51.100.9";

    @Autowired
    private CreateUserRepository createUserRepository;
    @MockitoSpyBean
    private PasswordEncoder passwordEncoder;

    private LogCapture logs;

    @BeforeEach
    void startCapturingLogs() {
        logs = LogCapture.ofRoot();
    }

    @AfterEach
    void stopCapturingLogs() {
        logs.close();
    }

    @Test
    void fiveFailedLogins_throttleTheAccount_evenWithTheRightPassword_untilTheWindowEnds() throws Exception {
        User user = createEnabledUser();
        for (int i = 0; i < 5; i++) {
            login(user.getPersonalId(), WRONG_PASSWORD, IP).andExpect(status().isUnauthorized());
        }

        expectThrottled(login(user.getPersonalId(), user.getPassword(), IP), 900);

        clock.advance(Duration.ofMinutes(15));
        login(user.getPersonalId(), user.getPassword(), IP).andExpect(status().isOk());
    }

    @Test
    void anUnknownPersonalId_isThrottledAtTheSamePoint_withTheSameResponse() throws Exception {
        User user = createEnabledUser();
        String unknownPersonalId = UserMother.randomPersonalId();
        for (int i = 0; i < 5; i++) {
            MvcResult existing = login(user.getPersonalId(), WRONG_PASSWORD, IP)
                    .andExpect(status().isUnauthorized()).andReturn();
            MvcResult unknown = login(unknownPersonalId, WRONG_PASSWORD, OTHER_IP)
                    .andExpect(status().isUnauthorized()).andReturn();
            Assertions.assertEquals(comparableBody(existing), comparableBody(unknown));
        }

        MvcResult existing = expectThrottled(login(user.getPersonalId(), WRONG_PASSWORD, IP), 900).andReturn();
        MvcResult unknown = expectThrottled(login(unknownPersonalId, WRONG_PASSWORD, OTHER_IP), 900).andReturn();

        Assertions.assertEquals(comparableBody(existing), comparableBody(unknown));
        Assertions.assertEquals(existing.getResponse().getHeader(HttpHeaders.RETRY_AFTER),
                unknown.getResponse().getHeader(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void twentyFailuresFromOneAddress_throttleThatAddressForAnyAccount_onBothEndpoints() throws Exception {
        User user = createEnabledUser();
        String token = token(user, OTHER_IP);
        for (int i = 0; i < 20; i++) {
            login(UserMother.randomPersonalId(), WRONG_PASSWORD, IP).andExpect(status().isUnauthorized());
        }

        expectThrottled(login(user.getPersonalId(), user.getPassword(), IP), 900);
        expectThrottled(changePassword(token, user.getPassword(), NEW_PASSWORD, IP), 900);

        // The account itself is not throttled: the same requests from another address go through
        login(user.getPersonalId(), user.getPassword(), OTHER_IP).andExpect(status().isOk());
    }

    @Test
    void aSuccessfulLogin_resetsTheAccountCounter_butNotTheAddressCounter() throws Exception {
        User user = createEnabledUser();
        for (int i = 0; i < 3; i++) {
            login(user.getPersonalId(), WRONG_PASSWORD, IP).andExpect(status().isUnauthorized());
        }

        login(user.getPersonalId(), user.getPassword(), IP).andExpect(status().isOk());

        // Without the reset, the account would be throttled after two more failures
        for (int i = 0; i < 4; i++) {
            login(user.getPersonalId(), WRONG_PASSWORD, IP).andExpect(status().isUnauthorized());
        }
        login(user.getPersonalId(), user.getPassword(), OTHER_IP).andExpect(status().isOk());

        // 7 failures from the address so far: 13 more reach its limit of 20 only if none was forgotten
        for (int i = 0; i < 13; i++) {
            login(UserMother.randomPersonalId(), WRONG_PASSWORD, IP).andExpect(status().isUnauthorized());
        }
        expectThrottled(login(UserMother.randomPersonalId(), WRONG_PASSWORD, IP), 900);
    }

    @Test
    void typingVariantsOfOnePersonalId_countAgainstTheSameAccount() throws Exception {
        User user = createEnabledUser("87654321X");

        for (String variant : List.of("87654321x", " 87654321 X ", "87654321-X", "87.654.321-X", "87654321X")) {
            login(variant, WRONG_PASSWORD, IP).andExpect(status().isUnauthorized());
        }

        expectThrottled(login("87654321X", user.getPassword(), OTHER_IP), 900);
    }

    @Test
    void fiveWrongCurrentPasswords_throttleTheChange_withoutCheckingThePassword_andKeepTheTokenValid()
            throws Exception {
        User user = createEnabledUser();
        String token = token(user, OTHER_IP);
        for (int i = 0; i < 5; i++) {
            changePassword(token, WRONG_PASSWORD, NEW_PASSWORD, IP).andExpect(status().isBadRequest());
        }
        clearInvocations(passwordEncoder);

        expectThrottled(changePassword(token, user.getPassword(), NEW_PASSWORD, IP), 900);

        verify(passwordEncoder, never()).matches(any(), any());
        mockMvc.perform(get(CURRENT_USER_URL).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
    }

    @Test
    void failedLoginsAndWrongCurrentPasswords_addUp_andThrottleBothEndpoints() throws Exception {
        User user = createEnabledUser();
        String token = token(user, OTHER_IP);
        for (int i = 0; i < 3; i++) {
            login(user.getPersonalId(), WRONG_PASSWORD, IP).andExpect(status().isUnauthorized());
        }
        for (int i = 0; i < 2; i++) {
            changePassword(token, WRONG_PASSWORD, NEW_PASSWORD, IP).andExpect(status().isBadRequest());
        }

        expectThrottled(login(user.getPersonalId(), user.getPassword(), OTHER_IP), 900);
        expectThrottled(changePassword(token, user.getPassword(), NEW_PASSWORD, OTHER_IP), 900);
    }

    @Test
    void aSuccessfulPasswordChange_resetsTheAccountCounter() throws Exception {
        User user = createEnabledUser();
        String token = token(user, OTHER_IP);
        for (int i = 0; i < 4; i++) {
            changePassword(token, WRONG_PASSWORD, NEW_PASSWORD, IP).andExpect(status().isBadRequest());
        }

        changePassword(token, user.getPassword(), NEW_PASSWORD, IP).andExpect(status().isNoContent());

        // Without the reset, the account would be throttled after one more failure
        for (int i = 0; i < 4; i++) {
            login(user.getPersonalId(), WRONG_PASSWORD, IP).andExpect(status().isUnauthorized());
        }
        login(user.getPersonalId(), NEW_PASSWORD, IP).andExpect(status().isOk());
    }

    @Test
    void unchangedPasswords_areNotCounted_soTheFirstWrongCurrentPasswordAfterThemIsTheFirstFailure()
            throws Exception {
        User user = createEnabledUser();
        String token = token(user, OTHER_IP);
        for (int i = 0; i < 6; i++) {
            expectUnchanged(changePassword(token, user.getPassword(), user.getPassword(), IP));
        }

        for (int i = 0; i < 5; i++) {
            changePassword(token, WRONG_PASSWORD, NEW_PASSWORD, IP)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].code").value("USER_CURRENT_PASSWORD_INCORRECT"));
        }

        expectThrottled(changePassword(token, user.getPassword(), NEW_PASSWORD, IP), 900);
    }

    @Test
    void anUnchangedPassword_doesNotResetTheAccountCounter() throws Exception {
        User user = createEnabledUser();
        String token = token(user, OTHER_IP);
        for (int i = 0; i < 4; i++) {
            changePassword(token, WRONG_PASSWORD, NEW_PASSWORD, IP).andExpect(status().isBadRequest());
        }

        expectUnchanged(changePassword(token, user.getPassword(), user.getPassword(), IP));
        changePassword(token, WRONG_PASSWORD, NEW_PASSWORD, IP).andExpect(status().isBadRequest());

        expectThrottled(changePassword(token, user.getPassword(), NEW_PASSWORD, IP), 900);
    }

    @Test
    void aWrongCurrentPassword_withTheSameValueAsTheNewOne_isReportedAsWrong_andCounted() throws Exception {
        User user = createEnabledUser();
        String token = token(user, OTHER_IP);
        for (int i = 0; i < 5; i++) {
            changePassword(token, WRONG_PASSWORD, WRONG_PASSWORD, IP)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].code").value("USER_CURRENT_PASSWORD_INCORRECT"));
        }

        expectThrottled(changePassword(token, user.getPassword(), NEW_PASSWORD, IP), 900);
    }

    @Test
    void aDisabledAccount_checksThePassword_andIsRejectedWithTheBadCredentialsResponse() throws Exception {
        User enabled = createEnabledUser();
        User disabled = createDisabledUser();

        clearInvocations(passwordEncoder);
        MvcResult wrongPassword = login(enabled.getPersonalId(), WRONG_PASSWORD, IP)
                .andExpect(status().isUnauthorized()).andReturn();
        verify(passwordEncoder, times(1)).matches(any(), any());

        clearInvocations(passwordEncoder);
        MvcResult disabledWrongPassword = login(disabled.getPersonalId(), WRONG_PASSWORD, IP)
                .andExpect(status().isUnauthorized()).andReturn();
        verify(passwordEncoder, times(1)).matches(any(), any());

        clearInvocations(passwordEncoder);
        MvcResult disabledRightPassword = login(disabled.getPersonalId(), disabled.getPassword(), IP)
                .andExpect(status().isUnauthorized()).andReturn();
        verify(passwordEncoder, times(1)).matches(any(), any());

        Assertions.assertEquals(comparableBody(wrongPassword), comparableBody(disabledWrongPassword));
        Assertions.assertEquals(comparableBody(wrongPassword), comparableBody(disabledRightPassword));
    }

    @Test
    void aFailedLogin_logsExactlyOneWarning_withTheMaskedAccount_theAddress_andTheReason() throws Exception {
        User enabled = createEnabledUser("11223344B");
        User disabled = createDisabledUser("55667788C");

        logs.clear();
        login(" 11.223.344-b ", WRONG_PASSWORD, IP).andExpect(status().isUnauthorized());
        assertSingleWarning("Failed login: account=***44B, ip=203.0.113.7, reason=BAD_CREDENTIALS",
                enabled.getPersonalId(), WRONG_PASSWORD);

        logs.clear();
        login(disabled.getPersonalId(), disabled.getPassword(), IP).andExpect(status().isUnauthorized());
        assertSingleWarning("Failed login: account=***88C, ip=203.0.113.7, reason=DISABLED",
                disabled.getPersonalId(), disabled.getPassword());
    }

    @Test
    void aWrongCurrentPassword_logsExactlyOneWarning_withTheUserId_theAddress_andTheReason() throws Exception {
        User user = createEnabledUser();
        String token = token(user, OTHER_IP);

        logs.clear();
        changePassword(token, WRONG_PASSWORD, NEW_PASSWORD, IP).andExpect(status().isBadRequest());

        assertSingleWarning("Failed password change: user=" + user.getId() + ", ip=203.0.113.7, "
                + "reason=WRONG_CURRENT_PASSWORD", user.getPersonalId(), WRONG_PASSWORD);
    }

    private User createEnabledUser() {
        return createEnabledUser(UserMother.randomPersonalId());
    }

    private User createEnabledUser(String personalId) {
        User user = UserMother.randomUserWithPersonalId(personalId);
        user.enable();
        createUserRepository.create(user);
        return user;
    }

    private User createDisabledUser() {
        return createDisabledUser(UserMother.randomPersonalId());
    }

    private User createDisabledUser(String personalId) {
        User user = UserMother.randomUserWithPersonalId(personalId);
        user.setEnabled(false);
        createUserRepository.create(user);
        return user;
    }

    private ResultActions login(String personalId, String password, String ip) throws Exception {
        return mockMvc.perform(post(LOGIN_URL)
                .with(from(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("username", personalId, "password", password))));
    }

    private String token(User user, String ip) throws Exception {
        String response = login(user.getPersonalId(), user.getPassword(), ip)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return "Bearer " + objectMapper.readTree(response).get("token").asText();
    }

    private ResultActions changePassword(String token, String currentPassword, String newPassword, String ip)
            throws Exception {
        return mockMvc.perform(put(CHANGE_PASSWORD_URL)
                .with(from(ip))
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "currentPassword", currentPassword,
                        "newPassword", newPassword))));
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    /**
     * The standard error body with the dedicated code, and the wait both in the header and in the body.
     */
    private static void expectUnchanged(ResultActions result) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value("USER_PASSWORD_UNCHANGED"));
    }

    private static ResultActions expectThrottled(ResultActions result, long retryAfterSeconds) throws Exception {
        return result.andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds)))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value("AUTH_TOO_MANY_FAILED_ATTEMPTS"))
                .andExpect(jsonPath("$.errors[0].params.retryAfterSeconds").value(String.valueOf(retryAfterSeconds)));
    }

    /**
     * The response body without the two fields that differ on every response.
     */
    private ObjectNode comparableBody(MvcResult result) throws Exception {
        ObjectNode body = (ObjectNode) objectMapper.readTree(result.getResponse().getContentAsString());
        body.remove("traceId");
        body.remove("timestamp");
        return body;
    }

    /**
     * Exactly one event at WARN or above for the request, with no stack trace, and nothing logged anywhere that
     * contains the password or the full personal ID.
     */
    private void assertSingleWarning(String message, String personalId, String password) {
        List<ILoggingEvent> events = logs.warningsAndAboveOfThisThread();
        Assertions.assertEquals(1, events.size(), () -> "Expected one warning, got " + events.stream()
                .map(ILoggingEvent::getFormattedMessage).toList());
        ILoggingEvent event = events.get(0);
        Assertions.assertEquals(Level.WARN, event.getLevel());
        Assertions.assertEquals(message, event.getFormattedMessage());
        Assertions.assertNull(event.getThrowableProxy());
        for (ILoggingEvent any : logs.allOfThisThread()) {
            Assertions.assertFalse(any.getFormattedMessage().contains(password), any::getFormattedMessage);
            Assertions.assertFalse(any.getFormattedMessage().contains(personalId), any::getFormattedMessage);
        }
    }
}
