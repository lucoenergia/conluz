package org.lucoenergia.conluz.infrastructure.admin.user.login;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.MembershipStateTestSupport;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.security.auth.AuthParameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother.PASSWORD;
import static org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother.PERSONAL_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Transactional
class LoginUserControllerTest extends BaseControllerTest {

    private static final String LOGIN_PATH = "/api/v1/login";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CommunityMembershipJpaRepository communityMembershipJpaRepository;

    /**
     * The token's payload is readable by anyone holding it, and no server code reads memberships from it: they are
     * read again from the database on every request (#390). So it lists none, in any state (#481).
     */
    @Test
    void theTokenCarriesNoCommunityMemberships_activeOrPending() throws Exception {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        Community active = createCommunityRepository.create(CommunityMother.random().build());
        Community pending = createCommunityRepository.create(CommunityMother.random().build());
        createMembershipRepository.create(active.getId(), user.getId(), CommunityRole.COMMUNITY_ADMIN);
        createMembershipRepository.create(pending.getId(), user.getId(), CommunityRole.COMMUNITY_MEMBER);
        MembershipStateTestSupport.markPending(communityMembershipJpaRepository, user.getId(), pending.getId());

        String token = loginUser(user).substring("Bearer ".length());

        String payloadText = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        JsonNode payload = objectMapper.readTree(payloadText);
        assertFalse(payload.has("community_memberships"), () -> "the token lists memberships: " + payloadText);
        assertFalse(payloadText.contains(active.getId().toString()), () -> "the token names a community: " + payloadText);
        assertFalse(payloadText.contains(pending.getId().toString()), () -> "the token names a community: " + payloadText);
    }

    /**
     * Nothing reads a role or a membership from the token: the server loads the user again on every request (#390).
     * So the token carries only what identifies and bounds it (#481).
     */
    @Test
    void theTokenCarriesOnlyItsSubjectIdAndValidity_evenForAPlatformAdmin() throws Exception {
        User user = UserMother.randomUser();
        user.enable();
        user.setPlatformAdmin(true);
        createUserRepository.create(user);

        String token = loginUser(user).substring("Bearer ".length());

        JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        Set<String> claims = new HashSet<>();
        payload.fieldNames().forEachRemaining(claims::add);
        assertEquals(Set.of("sub", "jti", "iat", "exp"), claims, () -> "the token carries: " + payload);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678A", "12345678a", " 12345678 A ", "12345678-A", "12.345.678-A"})
    void testLoginSucceedsWithAnyTypingVariantOfThePersonalId(String username) throws Exception {
        User user = UserMother.randomUserWithPersonalId("12345678A");
        user.enable();
        createUserRepository.create(user);

        String loginBody = objectMapper.writeValueAsString(
                Map.of("username", username, "password", user.getPassword()));

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void testLoginWithBadCredentials() throws Exception {

        String loginBody = "{\"username\": \"" + PERSONAL_ID + "\",\"password\": \"" + PASSWORD + "\"}";

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value("401"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testLoginWithoutBody() throws Exception {

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value("400"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testLoginWithMalformedBody() throws Exception {

        String malformedBody = "{username: " + PERSONAL_ID + ", password: " + PASSWORD + "}";

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedBody))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value("400"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testLoginWithBodyWithMissingField() throws Exception {

        String loginBody = "{\"username\": \"" + PERSONAL_ID + "\"}";

        // A body without a password is invalid, as any other invalid body (#479)
        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value("400"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testLoginWithBodyWithFieldNotExpected() throws Exception {

        String loginBody = String.format("""
                {
                  "username": "%s",
                  "password": "%s",
                  "unknown": "foo"
                }
                        """, PERSONAL_ID, PASSWORD);

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value("400"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testLoginWithBodyWithWrongField() throws Exception {

        String loginBody = String.format("""
                {
                  "username": "%s",
                  "pasword": "%s"
                }
                        """, PERSONAL_ID, PASSWORD);

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value("400"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testLoginWithSuccess() throws Exception {

        init();

        String loginBody = String.format("""
                {
                  "username": "%s",
                  "password": "%s"
                }
                        """, PERSONAL_ID, PASSWORD);

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(cookie().exists(AuthParameter.ACCESS_TOKEN.getCookieName()));
    }
}
