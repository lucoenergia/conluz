package org.lucoenergia.conluz.infrastructure.admin.user.disable;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother.PERSONAL_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class DisableUserControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private GetUserRepository getUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipService createMembershipService;
    @Autowired
    private UserRepository userRepository;

    private static final String CURRENT_USER_URL = "/api/v1/users/current";
    private static final String NEW_PASSWORD = "a brand new password for this user";

    @Test
    void testDisableUser() throws Exception {

        // Create a user enabled
        User user = UserMother.randomUser();
        user.setEnabled(true);
        createUserRepository.create(user);
        Assertions.assertTrue(getUserRepository.existsByPersonalId(UserPersonalId.of(user.getPersonalId())));

        // Login as default admin user
        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(post(String.format("/api/v1/users/%s/disable", user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        Assertions.assertFalse(getUserRepository.findByPersonalId(UserPersonalId.of(user.getPersonalId())).get().isEnabled());
    }

    @Test
    void testWithUnknownUser() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

        final String userId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/users/" + userId + "/disable")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithoutToken() throws Exception {

        final String userId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/users/" + userId + "/disable")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testAuthenticatedUserWithoutAdminRoleCannotAccess() throws Exception {

        String authHeader = loginAsPartner();

        final String userId = UUID.randomUUID().toString();

        // Test users endpoint
        mockMvc.perform(post("/api/v1/users/" + userId + "/disable")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    void testDisableLastPlatformAdminIsRejected() throws Exception {

        // Initialize the default platform admin.
        init();

        // Create a community and add the default platform admin as a member.
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User defaultAdmin = getUserRepository.findByPersonalId(UserPersonalId.of(PERSONAL_ID)).get();
        createMembershipService.create(community.getId(), defaultAdmin.getId(), CommunityRole.COMMUNITY_MEMBER);

        // Create a community admin of that community (not a platform admin) and log in.
        String authHeader = loginAsCommunityAdmin(community.getId());

        // The default admin is the only platform admin (count == 1). Disabling them
        // is rejected with 409, because the system can never be left with zero enabled platform admins.
        mockMvc.perform(post(String.format("/api/v1/users/%s/disable", defaultAdmin.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(HttpStatus.CONFLICT.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].code").value("USER_LAST_PLATFORM_ADMIN"))
                .andExpect(jsonPath("$.errors[0].params").value(nullValue()))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());

        // The default admin is still enabled.
        Assertions.assertTrue(getUserRepository.findByPersonalId(UserPersonalId.of(PERSONAL_ID))
                .get().isEnabled());
    }
    @Test
    void testCannotActOnOwnAccount() throws Exception {
        // Nobody may disable themselves, platform admin included: the guard settles the edit
        // decision first and then refuses because the target is the caller -- a 403, not a 404.
        String authHeader = loginAsDefaultPlatformAdmin();

        User self = getUserRepository.findByPersonalId(UserPersonalId.of(PERSONAL_ID)).get();

        mockMvc.perform(post(String.format("/api/v1/users/%s/disable", self.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()));
    }

    // --- Sessions of a disabled user (#346) ---

    @Test
    void aDisabledUser_isRejectedOnTheNextRequestWithTheirExistingToken() throws Exception {
        String adminToken = loginAsDefaultPlatformAdmin();
        User user = createEnabledUser();
        // Start at the beginning of a second, so the login and the disable most likely share it: the token is then
        // not older than the recorded disable, and only the enabled flag can reject it
        awaitTheNextSecond();
        String token = loginUser(user);
        getCurrentUser(token).andExpect(status().isOk());

        disable(adminToken, user).andExpect(status().isOk());

        getCurrentUser(token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void aDisabledUser_isRejected_evenWithNoDisableRecorded() throws Exception {
        User user = createEnabledUser();
        String token = loginUser(user);
        getCurrentUser(token).andExpect(status().isOk());

        // Only the flag changes: the token can be rejected by the enabled check alone
        UserEntity entity = userRepository.findById(user.getId()).orElseThrow();
        entity.setEnabled(false);
        userRepository.save(entity);

        getCurrentUser(token).andExpect(status().isUnauthorized());
    }

    @Test
    void aReEnabledUser_isRejectedWithATokenIssuedBeforeTheDisable_andAcceptedAfterLoggingInAgain() throws Exception {
        String adminToken = loginAsDefaultPlatformAdmin();
        User user = createEnabledUser();
        String tokenBeforeTheDisable = loginUser(user);
        awaitTheNextSecond();

        disable(adminToken, user).andExpect(status().isOk());
        enable(adminToken, user).andExpect(status().isOk());

        getCurrentUser(tokenBeforeTheDisable).andExpect(status().isUnauthorized());
        String tokenAfterTheEnable = loginUser(user);
        getCurrentUser(tokenAfterTheEnable).andExpect(status().isOk());
    }

    @Test
    void aUserNeverDisabled_keepsTheirTokens_andHasNoDisableRecorded() throws Exception {
        String adminToken = loginAsDefaultPlatformAdmin();
        User user = createEnabledUser();
        User someoneElse = createEnabledUser();
        String token = loginUser(user);
        awaitTheNextSecond();

        disable(adminToken, someoneElse).andExpect(status().isOk());

        getCurrentUser(token).andExpect(status().isOk());
        getCurrentUser(token).andExpect(status().isOk());
        Assertions.assertNull(reload(user).getDisabledAt());
    }

    @Test
    void passwordChangedThenDisabledAndReEnabled_onlyATokenIssuedAfterBothIsAccepted() throws Exception {
        String adminToken = loginAsDefaultPlatformAdmin();
        User user = createEnabledUser();
        String tokenBeforeThePasswordChange = loginUser(user);
        awaitTheNextSecond();
        changePassword(tokenBeforeThePasswordChange, user.getPassword(), NEW_PASSWORD)
                .andExpect(status().isNoContent());
        user.setPassword(NEW_PASSWORD);
        String tokenBeforeTheDisable = loginUser(user);
        awaitTheNextSecond();

        disable(adminToken, user).andExpect(status().isOk());
        enable(adminToken, user).andExpect(status().isOk());

        getCurrentUser(tokenBeforeThePasswordChange).andExpect(status().isUnauthorized());
        getCurrentUser(tokenBeforeTheDisable).andExpect(status().isUnauthorized());
        getCurrentUser(loginUser(user)).andExpect(status().isOk());
    }

    @Test
    void disabledAndReEnabledThenPasswordChanged_onlyATokenIssuedAfterBothIsAccepted() throws Exception {
        String adminToken = loginAsDefaultPlatformAdmin();
        User user = createEnabledUser();
        String tokenBeforeTheDisable = loginUser(user);
        awaitTheNextSecond();
        disable(adminToken, user).andExpect(status().isOk());
        enable(adminToken, user).andExpect(status().isOk());
        String tokenBeforeThePasswordChange = loginUser(user);
        String tokenUsedForThePasswordChange = loginUser(user);
        awaitTheNextSecond();

        changePassword(tokenUsedForThePasswordChange, user.getPassword(), NEW_PASSWORD)
                .andExpect(status().isNoContent());
        user.setPassword(NEW_PASSWORD);

        getCurrentUser(tokenBeforeTheDisable).andExpect(status().isUnauthorized());
        getCurrentUser(tokenBeforeThePasswordChange).andExpect(status().isUnauthorized());
        getCurrentUser(loginUser(user)).andExpect(status().isOk());
    }

    @Test
    void aUserDisabledTwice_theLatestDisableIsTheOneThatCounts() throws Exception {
        String adminToken = loginAsDefaultPlatformAdmin();
        User user = createEnabledUser();
        String tokenBeforeTheFirstDisable = loginUser(user);
        awaitTheNextSecond();
        disable(adminToken, user).andExpect(status().isOk());
        Instant firstDisable = reload(user).getDisabledAt();
        enable(adminToken, user).andExpect(status().isOk());
        String tokenBetweenTheDisables = loginUser(user);
        getCurrentUser(tokenBetweenTheDisables).andExpect(status().isOk());
        awaitTheNextSecond();

        disable(adminToken, user).andExpect(status().isOk());
        Instant secondDisable = reload(user).getDisabledAt();
        enable(adminToken, user).andExpect(status().isOk());

        Assertions.assertTrue(secondDisable.isAfter(firstDisable));
        getCurrentUser(tokenBeforeTheFirstDisable).andExpect(status().isUnauthorized());
        getCurrentUser(tokenBetweenTheDisables).andExpect(status().isUnauthorized());
        getCurrentUser(loginUser(user)).andExpect(status().isOk());
    }

    @Test
    void theDisableIsRecordedWithTheJvmClock_theOneTokensAreIssuedWith() throws Exception {
        String adminToken = loginAsDefaultPlatformAdmin();
        User user = createEnabledUser();
        // The application Clock bean is replaced in tests by a clock far from the JVM clock: a disable recorded
        // with it would be told apart from one recorded with the JVM clock
        Assertions.assertTrue(Duration.between(clock.instant(), Instant.now()).abs().toDays() > 1);

        Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);
        disable(adminToken, user).andExpect(status().isOk());
        Instant after = Instant.now();
        enable(adminToken, user).andExpect(status().isOk());

        Instant disabledAt = reload(user).getDisabledAt();
        Assertions.assertFalse(disabledAt.isBefore(before));
        Assertions.assertFalse(disabledAt.isAfter(after));
        getCurrentUser(loginUser(user)).andExpect(status().isOk());
    }

    private User createEnabledUser() {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        return user;
    }

    private User reload(User user) {
        return getUserRepository.findById(UserId.of(user.getId())).orElseThrow();
    }

    private ResultActions disable(String adminToken, User user) throws Exception {
        return mockMvc.perform(post(String.format("/api/v1/users/%s/disable", user.getId()))
                .header(HttpHeaders.AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON));
    }

    private ResultActions enable(String adminToken, User user) throws Exception {
        return mockMvc.perform(post(String.format("/api/v1/users/%s/enable", user.getId()))
                .header(HttpHeaders.AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON));
    }

    private ResultActions getCurrentUser(String token) throws Exception {
        return mockMvc.perform(get(CURRENT_USER_URL).header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions changePassword(String token, String currentPassword, String newPassword) throws Exception {
        return mockMvc.perform(put("/api/v1/users/current/password")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "currentPassword", currentPassword,
                        "newPassword", newPassword))));
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
