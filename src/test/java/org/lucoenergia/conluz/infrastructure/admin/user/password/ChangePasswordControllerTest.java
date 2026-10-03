package org.lucoenergia.conluz.infrastructure.admin.user.password;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthRepository;
import org.lucoenergia.conluz.domain.admin.user.auth.BlacklistedTokenRepository;
import org.lucoenergia.conluz.domain.admin.user.auth.Token;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.security.auth.AuthParameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class ChangePasswordControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/users/current/password";
    private static final String CURRENT_USER_URL = "/api/v1/users/current";
    private static final String NEW_PASSWORD = "a brand new password for this user";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private GetUserRepository getUserRepository;
    @Autowired
    private AuthRepository authRepository;
    @Autowired
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @Test
    void validChange_answers204_revokesEveryEarlierToken_andClearsTheFlag() throws Exception {
        User user = createFlaggedUser();
        String usedToken = login(user.getPersonalId(), user.getPassword());
        String otherToken = login(user.getPersonalId(), user.getPassword());
        // Both tokens must carry an iat in a second strictly before the change
        awaitTheNextSecond();

        changePassword(usedToken, user.getPassword(), NEW_PASSWORD)
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(AuthParameter.ACCESS_TOKEN.getCookieName(), 0));

        getCurrentUser(usedToken).andExpect(status().isUnauthorized());
        getCurrentUser(otherToken).andExpect(status().isUnauthorized());

        String newToken = login(user.getPersonalId(), NEW_PASSWORD);
        getCurrentUser(newToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));
        loginExpectingStatus(user.getPersonalId(), user.getPassword(), 401);

        User stored = getUserRepository.findById(UserId.of(user.getId())).orElseThrow();
        Assertions.assertFalse(stored.mustChangePassword());
        Assertions.assertNotNull(stored.getPasswordChangedAt());
    }

    @Test
    void validChange_revokesTheUsedTokenExplicitly_soItIsRejectedEvenWithinTheSameSecond() throws Exception {
        User user = createFlaggedUser();
        String usedToken = login(user.getPersonalId(), user.getPassword());

        changePassword(usedToken, user.getPassword(), NEW_PASSWORD).andExpect(status().isNoContent());

        String jti = authRepository.getJtiFromToken(Token.of(usedToken.substring("Bearer ".length())))
                .orElseThrow();
        Assertions.assertTrue(blacklistedTokenRepository.existsByJti(jti));
        getCurrentUser(usedToken).andExpect(status().isUnauthorized());
    }

    @Test
    void loggingInRightAfterTheChange_givesATokenThatIsAccepted_whileTheOldOneIsRejected() throws Exception {
        User user = createFlaggedUser();
        String oldToken = login(user.getPersonalId(), user.getPassword());

        changePassword(oldToken, user.getPassword(), NEW_PASSWORD).andExpect(status().isNoContent());
        // No wait: this login usually falls within the same second as the change
        String newToken = login(user.getPersonalId(), NEW_PASSWORD);

        getCurrentUser(newToken).andExpect(status().isOk());
        getCurrentUser(oldToken).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenIssuedAfterTheChange_isAccepted_evenSecondsLater() throws Exception {
        User user = createFlaggedUser();
        String oldToken = login(user.getPersonalId(), user.getPassword());
        changePassword(oldToken, user.getPassword(), NEW_PASSWORD).andExpect(status().isNoContent());

        awaitTheNextSecond();
        String newToken = login(user.getPersonalId(), NEW_PASSWORD);

        getCurrentUser(newToken).andExpect(status().isOk());
    }

    @Test
    void wrongCurrentPassword_answers400WithItsOwnCode_neverA401_andChangesNothing() throws Exception {
        User user = createFlaggedUser();
        String token = login(user.getPersonalId(), user.getPassword());

        changePassword(token, "not the current password at all", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("USER_CURRENT_PASSWORD_INCORRECT"));

        getCurrentUser(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        login(user.getPersonalId(), user.getPassword());
        loginExpectingStatus(user.getPersonalId(), NEW_PASSWORD, 401);
        User stored = getUserRepository.findById(UserId.of(user.getId())).orElseThrow();
        Assertions.assertNull(stored.getPasswordChangedAt());
    }

    @Test
    void newPasswordOf14CodePoints_isRefused() throws Exception {
        User user = createFlaggedUser();
        String token = login(user.getPersonalId(), user.getPassword());

        expectPolicyViolation(changePassword(token, user.getPassword(), "a".repeat(14)), "TOO_SHORT");

        getCurrentUser(token).andExpect(status().isOk());
        login(user.getPersonalId(), user.getPassword());
    }

    @Test
    void newPasswordOf15CodePoints_isAccepted() throws Exception {
        User user = createFlaggedUser();
        String token = login(user.getPersonalId(), user.getPassword());

        changePassword(token, user.getPassword(), "a".repeat(15)).andExpect(status().isNoContent());

        login(user.getPersonalId(), "a".repeat(15));
    }

    @Test
    void newPasswordOf64CodePointsWithSpacesAndAccents_isAccepted_andOnlyTheExactValueLogsIn() throws Exception {
        String password = " contraseña: el ñandú corre por la pampa, sin prisa y sin pausa ";
        Assertions.assertEquals(64, password.codePointCount(0, password.length()));
        Assertions.assertTrue(password.getBytes(StandardCharsets.UTF_8).length <= 72);
        User user = createFlaggedUser();
        String token = login(user.getPersonalId(), user.getPassword());

        changePassword(token, user.getPassword(), password).andExpect(status().isNoContent());

        login(user.getPersonalId(), password);
        loginExpectingStatus(user.getPersonalId(), password.trim(), 401);
    }

    @Test
    void newPasswordOverSeventyTwoBytes_isRefusedWithTheBytesRule_andNeverTruncated() throws Exception {
        // 37 code points, 74 bytes: within the length rule, over BCrypt's input limit
        String password = "ñ".repeat(37);
        User user = createFlaggedUser();
        String token = login(user.getPersonalId(), user.getPassword());

        expectPolicyViolation(changePassword(token, user.getPassword(), password), "TOO_MANY_BYTES");

        loginExpectingStatus(user.getPersonalId(), password, 401);
        loginExpectingStatus(user.getPersonalId(), "ñ".repeat(36), 401);
        login(user.getPersonalId(), user.getPassword());
    }

    @Test
    void newPasswordOver64CodePoints_isRefusedWithTheLengthRule() throws Exception {
        User user = createFlaggedUser();
        String token = login(user.getPersonalId(), user.getPassword());

        expectPolicyViolation(changePassword(token, user.getPassword(), "a".repeat(65)), "TOO_LONG");
    }

    @Test
    void longLowercaseOnlyPassword_isAccepted() throws Exception {
        User user = createFlaggedUser();
        String token = login(user.getPersonalId(), user.getPassword());

        changePassword(token, user.getPassword(), "correcthorsebatterystaple").andExpect(status().isNoContent());

        login(user.getPersonalId(), "correcthorsebatterystaple");
    }

    @Test
    void newPasswordEqualToTheCurrentOne_isAccepted() throws Exception {
        User user = createFlaggedUser();
        String token = login(user.getPersonalId(), user.getPassword());

        changePassword(token, user.getPassword(), user.getPassword()).andExpect(status().isNoContent());

        String newToken = login(user.getPersonalId(), user.getPassword());
        getCurrentUser(newToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));
    }

    @Test
    void missingFields_answer400() throws Exception {
        User user = createFlaggedUser();
        String token = login(user.getPersonalId(), user.getPassword());

        Map<String, String> withoutCurrent = new HashMap<>();
        withoutCurrent.put("newPassword", NEW_PASSWORD);
        mockMvc.perform(put(URL)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withoutCurrent)))
                .andExpect(status().isBadRequest());

        Map<String, String> withoutNew = new HashMap<>();
        withoutNew.put("currentPassword", user.getPassword());
        mockMvc.perform(put(URL)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withoutNew)))
                .andExpect(status().isBadRequest());

        login(user.getPersonalId(), user.getPassword());
    }

    @Test
    void missingToken_answers401() throws Exception {
        mockMvc.perform(put(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "whatever the password is",
                                "newPassword", NEW_PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    private User createFlaggedUser() {
        User user = UserMother.randomUser();
        user.enable();
        user.requirePasswordChange();
        createUserRepository.create(user);
        return user;
    }

    private String login(String personalId, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(personalId, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return "Bearer " + objectMapper.readTree(response).get("token").asText();
    }

    private void loginExpectingStatus(String personalId, String password, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(personalId, password)))
                .andExpect(status().is(expectedStatus));
    }

    private String loginBody(String personalId, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("username", personalId, "password", password));
    }

    private ResultActions changePassword(String token, String currentPassword, String newPassword) throws Exception {
        return mockMvc.perform(put(URL)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content(objectMapper.writeValueAsString(Map.of(
                        "currentPassword", currentPassword,
                        "newPassword", newPassword))));
    }

    private ResultActions getCurrentUser(String token) throws Exception {
        return mockMvc.perform(get(CURRENT_USER_URL).header(HttpHeaders.AUTHORIZATION, token));
    }

    private static void expectPolicyViolation(ResultActions result, String rule) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("USER_PASSWORD_POLICY_VIOLATION"))
                .andExpect(jsonPath("$.errors[0].params.rule").value(rule));
    }

    /**
     * Blocks until the wall clock has moved into a later second than the one it is in now, so that every token
     * issued before the call carries an {@code iat} strictly earlier than anything that happens after it.
     */
    private static void awaitTheNextSecond() throws InterruptedException {
        long currentSecond = Instant.now().getEpochSecond();
        while (Instant.now().getEpochSecond() <= currentSecond) {
            Thread.sleep(50);
        }
    }
}
