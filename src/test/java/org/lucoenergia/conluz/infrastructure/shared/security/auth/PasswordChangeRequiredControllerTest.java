package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother.PASSWORD;
import static org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother.PERSONAL_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A user who must change their password is refused every request but reading the current user, changing the
 * password and logging out (#342).
 */
@Transactional
class PasswordChangeRequiredControllerTest extends BaseControllerTest {

    private static final String NEW_PASSWORD = "a brand new password for this user";
    private static final String NEW_EMAIL = "a.new.address@example.org";

    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CommunityJpaRepository communityJpaRepository;

    private Community community;

    @BeforeEach
    void createCommunity() {
        community = createCommunityRepository.create(CommunityMother.random().build());
    }

    // --- refused ---

    @Test
    void aRead_isRefused() throws Exception {
        String token = loginUser(createCommunityMemberWhoMustChangePassword(community.getId()));

        expectPasswordChangeRequired(getCommunity(token));
    }

    @Test
    void aWrite_isRefused_andChangesNothing() throws Exception {
        User user = createCommunityMemberWhoMustChangePassword(community.getId());
        String token = loginUser(user);

        expectPasswordChangeRequired(updateProfile(token));

        Assertions.assertEquals(user.getEmail(),
                userRepository.findByPersonalId(user.getPersonalId()).orElseThrow().getEmail());
    }

    @Test
    void aCommunityScopedAction_isRefused_toACommunityAdmin_andChangesNothing() throws Exception {
        String token = loginUser(createCommunityAdminWhoMustChangePassword(community.getId()));
        String personalId = "34400001A";

        expectPasswordChangeRequired(createUserInTheCommunity(token, personalId));

        Assertions.assertFalse(userRepository.existsByPersonalId(personalId));
    }

    @Test
    void aPlatformAdminAction_isRefused_toAPlatformAdmin_andChangesNothing() throws Exception {
        initDefaultPlatformAdminWhoMustChangePassword();
        String token = login(PERSONAL_ID, PASSWORD);
        String code = "CODE-" + UUID.randomUUID();

        expectPasswordChangeRequired(createCommunity(token, code));

        Assertions.assertFalse(communityJpaRepository.existsByCode(code));
    }

    @Test
    void aResourceThatDoesNotExist_isRefusedTheSameWay_beforeItIsLookedUp() throws Exception {
        String token = loginUser(createCommunityMemberWhoMustChangePassword(community.getId()));

        expectPasswordChangeRequired(mockMvc.perform(get("/api/v1/communities/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, token)));
    }

    @Test
    void theRefusal_carriesTheLocalisedMessage() throws Exception {
        String token = loginUser(createCommunityMemberWhoMustChangePassword(community.getId()));

        getCommunity(token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Debe cambiar su contraseña antes de continuar."))
                .andExpect(jsonPath("$.errors[0].message").value("Debe cambiar su contraseña antes de continuar."));
    }

    @Test
    void aRefusal_logsNothing() throws Exception {
        String token = loginUser(createCommunityMemberWhoMustChangePassword(community.getId()));

        try (LogCapture logs = LogCapture.ofRoot()) {
            expectPasswordChangeRequired(getCommunity(token));
            expectPasswordChangeRequired(updateProfile(token));

            List<ILoggingEvent> events = logs.allOfThisThread();
            Assertions.assertTrue(events.stream().noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.WARN)),
                    () -> "unexpected warnings or errors: " + events);
            Assertions.assertTrue(events.stream().noneMatch(event -> event.getThrowableProxy() != null),
                    () -> "unexpected stack traces: " + events);
        }
    }

    // --- allowed ---

    @Test
    void readingTheCurrentUser_isAllowed() throws Exception {
        String token = loginUser(createCommunityMemberWhoMustChangePassword(community.getId()));

        mockMvc.perform(get("/api/v1/users/current").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void changingThePassword_isAllowed() throws Exception {
        User user = createCommunityMemberWhoMustChangePassword(community.getId());
        String token = loginUser(user);

        changePassword(token, user.getPassword()).andExpect(status().isNoContent());
    }

    @Test
    void loggingOut_isAllowed() throws Exception {
        String token = loginUser(createCommunityMemberWhoMustChangePassword(community.getId()));

        mockMvc.perform(post("/api/v1/logout").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/current").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    // --- after the password is changed ---

    @Test
    void afterChangingThePassword_aMemberReachesWhatWasRefused() throws Exception {
        User user = createCommunityMemberWhoMustChangePassword(community.getId());
        changePassword(loginUser(user), user.getPassword()).andExpect(status().isNoContent());
        String token = login(user.getPersonalId(), NEW_PASSWORD);

        getCommunity(token).andExpect(status().isOk());
        updateProfile(token).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/current").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));
    }

    @Test
    void afterChangingThePassword_aCommunityAdminReachesWhatWasRefused() throws Exception {
        User user = createCommunityAdminWhoMustChangePassword(community.getId());
        changePassword(loginUser(user), user.getPassword()).andExpect(status().isNoContent());
        String token = login(user.getPersonalId(), NEW_PASSWORD);

        createUserInTheCommunity(token, "34400002B").andExpect(status().isOk());
    }

    @Test
    void afterChangingThePassword_aPlatformAdminReachesWhatWasRefused() throws Exception {
        initDefaultPlatformAdminWhoMustChangePassword();
        changePassword(login(PERSONAL_ID, PASSWORD), PASSWORD).andExpect(status().isNoContent());
        String token = login(PERSONAL_ID, NEW_PASSWORD);

        createCommunity(token, "CODE-" + UUID.randomUUID()).andExpect(status().isOk());
    }

    // --- unauthenticated endpoints ---

    @Test
    void loggingIn_isUnaffected() throws Exception {
        User user = createCommunityMemberWhoMustChangePassword(community.getId());

        login(user.getPersonalId(), user.getPassword());
    }

    @Test
    void thePublicInfo_isUnaffected_withTheTokenOfAUserWhoMustChangeTheirPassword_andAnonymously() throws Exception {
        String token = loginUser(createCommunityMemberWhoMustChangePassword(community.getId()));

        mockMvc.perform(get("/api/v1/info").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/info"))
                .andExpect(status().isOk());
    }

    @Test
    void init_answersAsBefore_withTheTokenOfAUserWhoMustChangeTheirPassword_andAnonymously() throws Exception {
        initDefaultPlatformAdminWhoMustChangePassword();
        String token = login(PERSONAL_ID, PASSWORD);
        String body = objectMapper.writeValueAsString(Map.of("defaultAdminUser", Map.of(
                "personalId", "34400003C", "password", NEW_PASSWORD, "fullName", "Another admin",
                "email", "another.admin@example.org")));

        // Already initialised: the init endpoint's own refusal, not the password change one
        mockMvc.perform(post("/api/v1/init")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").doesNotExist());
        mockMvc.perform(post("/api/v1/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").doesNotExist());
    }

    private static void expectPasswordChangeRequired(ResultActions result) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value("USER_PASSWORD_CHANGE_REQUIRED"));
    }

    private String login(String personalId, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", personalId, "password", password))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return "Bearer " + objectMapper.readTree(response).get("token").asText();
    }

    private ResultActions changePassword(String token, String currentPassword) throws Exception {
        return mockMvc.perform(put("/api/v1/users/current/password")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content(objectMapper.writeValueAsString(Map.of(
                        "currentPassword", currentPassword,
                        "newPassword", NEW_PASSWORD))));
    }

    private ResultActions getCommunity(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/communities/" + community.getId())
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions updateProfile(String token) throws Exception {
        return mockMvc.perform(put("/api/v1/users/profile")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", NEW_EMAIL))));
    }

    private ResultActions createUserInTheCommunity(String token, String personalId) throws Exception {
        return mockMvc.perform(post("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "personalId", personalId,
                        "fullName", "John Doe",
                        "number", 1,
                        "email", personalId.toLowerCase() + "@example.org",
                        "password", "a secure password1!",
                        "communityId", community.getId().toString(),
                        "communityRole", "COMMUNITY_MEMBER"))));
    }

    private ResultActions createCommunity(String token, String code) throws Exception {
        return mockMvc.perform(post("/api/v1/communities")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "name", "A brand new community",
                        "code", code,
                        "legalId", "LEGAL-" + UUID.randomUUID()))));
    }
}
