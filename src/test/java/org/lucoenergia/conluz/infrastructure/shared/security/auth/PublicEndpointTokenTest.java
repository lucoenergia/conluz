package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoints that require no authentication ignore any token presented on them (#347): a stale token never stands
 * in the way of logging in again, and a valid one does not authenticate the request.
 */
@Transactional
class PublicEndpointTokenTest extends BaseControllerTest {

    private static final String LOGIN_URL = "/api/v1/login";
    private static final String INFO_URL = "/api/v1/info";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtConfiguration jwtConfiguration;
    @MockitoSpyBean
    private UserDetailsService userDetailsService;

    private CraftedTokens tokens;
    private LogCapture logs;

    @BeforeEach
    void setUp() {
        tokens = new CraftedTokens(jwtConfiguration.getSecretKey());
        logs = LogCapture.ofRoot();
    }

    @AfterEach
    void stopCapturingLogs() {
        logs.close();
    }

    @Test
    void loginSucceeds_whateverInvalidTokenItCarries_inTheHeaderOrTheCookie() throws Exception {
        User user = createEnabledUser();
        Map<String, String> invalidTokens = invalidTokensOf(user);

        for (Map.Entry<String, String> invalid : invalidTokens.entrySet()) {
            String token = invalid.getValue();
            expectLoggedIn(user, login(user).header(HttpHeaders.AUTHORIZATION, "Bearer " + token),
                    invalid.getKey() + " in the header");
            expectLoggedIn(user, login(user).cookie(new Cookie("access_token", token)),
                    invalid.getKey() + " in the cookie");
        }
    }

    @Test
    void aPublicEndpoint_answersAnInvalidToken_asItAnswersNoToken() throws Exception {
        User user = createEnabledUser();
        ObjectNode withoutAToken = comparable(mockMvc.perform(get(INFO_URL)).andExpect(status().isOk()).andReturn());
        Map<String, String> invalidTokens = invalidTokensOf(user);
        invalidTokens.put("malformed", "not-a-jwt");
        invalidTokens.put("user no longer exists", tokens.valid(UUID.randomUUID()));

        for (Map.Entry<String, String> invalid : invalidTokens.entrySet()) {
            for (Function<String, MockHttpServletRequestBuilder> carrying : carriers()) {
                logs.clear();

                MvcResult result = mockMvc.perform(carrying.apply(invalid.getValue())).andReturn();

                Assertions.assertEquals(200, result.getResponse().getStatus(), invalid.getKey());
                Assertions.assertEquals(withoutAToken, comparable(result), invalid.getKey());
                expectNothingLogged(invalid.getKey());
            }
        }
    }

    @Test
    void aPublicEndpoint_answersAValidToken_asItAnswersNoToken_withoutAuthenticatingIt() throws Exception {
        User user = createEnabledUser();
        String token = rawToken(loginUser(user));
        ObjectNode withoutAToken = comparable(mockMvc.perform(get(INFO_URL)).andExpect(status().isOk()).andReturn());

        for (Function<String, MockHttpServletRequestBuilder> carrying : carriers()) {
            clearInvocations(userDetailsService);
            logs.clear();

            MvcResult result = mockMvc.perform(carrying.apply(token)).andExpect(status().isOk()).andReturn();

            Assertions.assertEquals(withoutAToken, comparable(result));
            // The token's user is never loaded, so the request is never authenticated as them
            verify(userDetailsService, never()).loadUserByUsername(any());
            expectNothingLogged("valid token");
        }
    }

    /**
     * Tokens a returning user's browser may still hold, none of which would authenticate a request.
     */
    private Map<String, String> invalidTokensOf(User user) throws Exception {
        String revoked = rawToken(loginUser(user));
        mockMvc.perform(post("/api/v1/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + revoked))
                .andExpect(status().isOk());
        UserEntity entity = userRepository.findById(user.getId()).orElseThrow();
        Instant now = Instant.now();
        entity.setPasswordChangedAt(now);
        entity.setDisabledAt(now);
        userRepository.save(entity);
        String issuedEarlier = tokens.issuedAt(user.getId(), now.minusSeconds(60));

        Map<String, String> invalidTokens = new LinkedHashMap<>();
        invalidTokens.put("tampered", tokens.tampered(user.getId()));
        invalidTokens.put("other key", tokens.signedWithOtherKey(user.getId()));
        invalidTokens.put("alg none", tokens.unsigned(user.getId()));
        invalidTokens.put("expired", tokens.expired(user.getId()));
        invalidTokens.put("revoked", revoked);
        // The user was disabled and enabled again, and changed their password, after this token was issued
        invalidTokens.put("issued before the last disable and password change", issuedEarlier);
        return invalidTokens;
    }

    private MockHttpServletRequestBuilder login(User user) throws Exception {
        return post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "username", user.getPersonalId(),
                        "password", user.getPassword())));
    }

    private void expectLoggedIn(User user, MockHttpServletRequestBuilder login, String carrying) throws Exception {
        logs.clear();

        MvcResult result = mockMvc.perform(login).andReturn();

        Assertions.assertEquals(200, result.getResponse().getStatus(), carrying);
        String token = objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
        mockMvc.perform(get("/api/v1/users/current").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()));
        expectNothingLogged(carrying);
    }

    private static List<Function<String, MockHttpServletRequestBuilder>> carriers() {
        return List.of(
                token -> get(INFO_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token),
                token -> get(INFO_URL).cookie(new Cookie("access_token", token)));
    }

    private void expectNothingLogged(String carrying) {
        Assertions.assertEquals(List.of(), logs.warningsAndAboveOfThisThread().stream()
                .map(ILoggingEvent::getFormattedMessage).toList(), carrying);
    }

    private ObjectNode comparable(MvcResult result) throws Exception {
        return (ObjectNode) objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private User createEnabledUser() {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        return user;
    }

    private static String rawToken(String bearer) {
        return bearer.substring("Bearer ".length());
    }
}
