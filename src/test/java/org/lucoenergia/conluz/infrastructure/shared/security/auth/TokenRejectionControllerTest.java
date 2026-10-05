package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.auth.TokenRejectionReason;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every token rejected on an authenticated endpoint answers the standard 401 and is logged as a single warning,
 * with no token anywhere in the logs (#347).
 */
@Transactional
class TokenRejectionControllerTest extends BaseControllerTest {

    private static final String CURRENT_USER_URL = "/api/v1/users/current";
    private static final String IP = "203.0.113.7";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtConfiguration jwtConfiguration;
    @Autowired
    private MessageSource messageSource;

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
    void aTokenWithATamperedSignature_isRejected() throws Exception {
        User user = createEnabledUser();

        expectRejected(tokens.tampered(user.getId()), TokenRejectionReason.INVALID_SIGNATURE, null);
    }

    @Test
    void aTokenSignedWithAnotherKey_isRejected() throws Exception {
        User user = createEnabledUser();

        expectRejected(tokens.signedWithOtherKey(user.getId()), TokenRejectionReason.INVALID_SIGNATURE, null);
    }

    @Test
    void anUnsignedToken_isRejected() throws Exception {
        User user = createEnabledUser();

        expectRejected(tokens.unsigned(user.getId()), TokenRejectionReason.UNSUPPORTED, null);
        // jjwt checks the expiry before noticing there is no signature: the subject is still not trusted
        expectRejected(tokens.expiredUnsigned(user.getId()), TokenRejectionReason.UNSUPPORTED, null);
    }

    @Test
    void aTokenWithAnUnsupportedAlgorithm_isRejected() throws Exception {
        User user = createEnabledUser();

        expectRejected(tokens.signedWithRsa(user.getId()), TokenRejectionReason.UNSUPPORTED, null);
        expectRejected(tokens.signedWithHs512(user.getId()), TokenRejectionReason.UNSUPPORTED, null);
        // jjwt reports an unknown algorithm name with the same exception as a signature that does not match
        expectRejected(tokens.unknownAlgorithm(user.getId()), TokenRejectionReason.INVALID_SIGNATURE, null);
    }

    @Test
    void aMalformedToken_isRejected() throws Exception {
        User user = createEnabledUser();

        expectRejected("not-a-jwt", TokenRejectionReason.MALFORMED, null);
        expectRejected("!!!.!!!.!!!", TokenRejectionReason.MALFORMED, null);
        expectRejected(tokens.headerNotJson(user.getId()), TokenRejectionReason.MALFORMED, null);
        expectRejected("", TokenRejectionReason.MALFORMED, null);
    }

    @Test
    void anExpiredToken_isRejected_namingItsVerifiedUser() throws Exception {
        User user = createEnabledUser();

        expectRejected(tokens.expired(user.getId()), TokenRejectionReason.EXPIRED, user.getId());
    }

    @Test
    void aSignedTokenWithoutTheClaimsEveryIssuedTokenHas_isRejected() throws Exception {
        User user = createEnabledUser();
        Instant now = Instant.now();
        String subject = user.getId().toString();

        expectRejected(tokens.signed(b -> b.setId(UUID.randomUUID().toString())
                        .setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(60)))),
                TokenRejectionReason.MISSING_CLAIMS, null);
        expectRejected(tokens.signed(b -> b.setSubject(subject)
                        .setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(60)))),
                TokenRejectionReason.MISSING_CLAIMS, null);
        expectRejected(tokens.signed(b -> b.setSubject(subject).setId(UUID.randomUUID().toString())
                        .setIssuedAt(Date.from(now))),
                TokenRejectionReason.MISSING_CLAIMS, null);
        expectRejected(tokens.signed(b -> b.setSubject("not-a-user-id").setId(UUID.randomUUID().toString())
                        .setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(60)))),
                TokenRejectionReason.INVALID_CLAIMS, null);
    }

    @Test
    void aRevokedToken_isRejected() throws Exception {
        User user = createEnabledUser();
        String token = rawToken(loginUser(user));
        mockMvc.perform(post("/api/v1/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        expectRejected(token, TokenRejectionReason.REVOKED, user.getId());
    }

    @Test
    void aDisabledUsersToken_isRejected() throws Exception {
        User user = createEnabledUser();
        String token = tokens.valid(user.getId());
        update(user, entity -> entity.setEnabled(false));

        expectRejected(token, TokenRejectionReason.USER_DISABLED, user.getId());
    }

    @Test
    void aTokenIssuedBeforeThePasswordChange_isRejected() throws Exception {
        User user = createEnabledUser();
        update(user, entity -> entity.setPasswordChangedAt(Instant.now()));

        expectRejected(tokens.issuedAt(user.getId(), Instant.now().minusSeconds(60)),
                TokenRejectionReason.ISSUED_BEFORE_PASSWORD_CHANGE, user.getId());
    }

    @Test
    void aTokenIssuedBeforeTheLastDisable_isRejected_afterTheUserIsEnabledAgain() throws Exception {
        User user = createEnabledUser();
        update(user, entity -> entity.setDisabledAt(Instant.now()));

        expectRejected(tokens.issuedAt(user.getId(), Instant.now().minusSeconds(60)),
                TokenRejectionReason.ISSUED_BEFORE_DISABLE, user.getId());
    }

    @Test
    void aTokenWhoseUserNoLongerExists_isRejected() throws Exception {
        UUID deletedUserId = UUID.randomUUID();

        expectRejected(tokens.valid(deletedUserId), TokenRejectionReason.USER_NOT_FOUND, deletedUserId);
    }

    @Test
    void aTokenInTheCookie_isRejectedTheSameWay() throws Exception {
        User user = createEnabledUser();
        String token = tokens.tampered(user.getId());

        expectRejected(token, get(CURRENT_USER_URL).cookie(new Cookie("access_token", token)),
                TokenRejectionReason.INVALID_SIGNATURE, null);
    }

    @Test
    void aValidToken_isAccepted_andNothingIsLogged() throws Exception {
        User user = createEnabledUser();
        String token = loginUser(user);
        logs.clear();

        mockMvc.perform(get(CURRENT_USER_URL).with(from(IP)).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()));

        Assertions.assertEquals(List.of(), logs.warningsAndAboveOfThisThread().stream()
                .map(ILoggingEvent::getFormattedMessage).toList());
    }

    private void expectRejected(String token, TokenRejectionReason reason, UUID user) throws Exception {
        expectRejected(token, get(CURRENT_USER_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token),
                reason, user);
    }

    /**
     * The standard 401 body; exactly one warning, naming the reason, the user only when known, the client address
     * and the trace id of the response; no error, no stack trace, and no trace of the token in anything logged.
     */
    private void expectRejected(String token, MockHttpServletRequestBuilder request, TokenRejectionReason reason,
                                UUID user) throws Exception {
        logs.clear();
        String unauthorized = messageSource.getMessage("error.unauthorized", null, Locale.ENGLISH);

        MvcResult result = mockMvc.perform(request.with(from(IP)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(unauthorized))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].message").value(unauthorized))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andReturn();
        String traceId = objectMapper.readTree(result.getResponse().getContentAsString()).get("traceId").asText();

        List<ILoggingEvent> warnings = logs.warningsAndAboveOfThisThread();
        Assertions.assertEquals(1, warnings.size(), () -> reason + ": expected one warning, got " + warnings.stream()
                .map(ILoggingEvent::getFormattedMessage).toList());
        Assertions.assertEquals(Level.WARN, warnings.get(0).getLevel());
        Assertions.assertEquals("Token rejected: reason=" + reason + ", user=" + (user == null ? "unknown" : user)
                + ", ip=" + IP + ", traceId=" + traceId, warnings.get(0).getFormattedMessage());

        List<String> secrets = Arrays.stream(token.split("\\.", -1)).filter(segment -> !segment.isEmpty()).toList();
        for (ILoggingEvent event : logs.allOfThisThread()) {
            Assertions.assertNull(event.getThrowableProxy(), event::getFormattedMessage);
            for (String secret : secrets) {
                Assertions.assertFalse(event.getFormattedMessage().contains(secret), event::getFormattedMessage);
            }
        }
    }

    private User createEnabledUser() {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        return user;
    }

    private void update(User user, Consumer<UserEntity> change) {
        UserEntity entity = userRepository.findById(user.getId()).orElseThrow();
        change.accept(entity);
        userRepository.save(entity);
    }

    private static String rawToken(String bearer) {
        return bearer.substring("Bearer ".length());
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
