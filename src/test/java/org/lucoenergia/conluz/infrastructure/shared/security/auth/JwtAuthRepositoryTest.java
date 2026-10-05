package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.Token;
import org.lucoenergia.conluz.domain.admin.user.auth.TokenRejectionReason;
import org.lucoenergia.conluz.domain.admin.user.auth.VerifiedToken;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.shared.security.JwtSecretKeyGenerator;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@ExtendWith(MockitoExtension.class)
class JwtAuthRepositoryTest {

    private final static String SECRET_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @InjectMocks
    private JwtAuthRepository repository;

    @Mock
    private JwtConfiguration jwtConfiguration;

    @Test
    void testGetAValidToken() {
        User user = UserMother.randomUser();
        user.enable();

        mockJwtConfig();

        Token token = repository.getToken(user);
        Assertions.assertNotNull(token);

        Assertions.assertEquals(Optional.empty(), repository.findRejectionReason(token, user));
    }

    @Test
    void testTokenClaims() {
        User user = UserMother.randomUser();
        user.enable();

        mockJwtConfig();

        Token token = repository.getToken(user);

        Assertions.assertNotNull(token);
        Assertions.assertEquals(Optional.empty(), repository.findRejectionReason(token, user));
        Assertions.assertEquals(user.getId(), repository.verify(token).userId());
    }

    @Test
    void testGetJtiFromToken() {
        User user = UserMother.randomUser();

        mockJwtConfig();

        Token token = repository.getToken(user);

        Optional<String> jti = repository.getJtiFromToken(token);
        Assertions.assertNotNull(jti);
        Assertions.assertTrue(jti.isPresent());
    }

    @Test
    void testGetUserIdByInvalidToken() {
        String invalidToken = "invalid-token";

        Mockito.when(jwtConfiguration.getSecretKey()).thenReturn(SECRET_KEY);

        InvalidTokenException exception = Assertions.assertThrows(InvalidTokenException.class,
                () -> repository.verify(Token.of(invalidToken)));

        Assertions.assertEquals(TokenRejectionReason.MALFORMED, exception.getReason());
        Assertions.assertTrue(exception.getUserId().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @NullSource
    void testMissingSecretKey(String secretKey) {
        String invalidToken = "invalid-token";

        Mockito.when(jwtConfiguration.getSecretKey()).thenReturn(secretKey);

        Assertions.assertThrows(SecretKeyNotFoundException.class,
                () -> repository.verify(Token.of(invalidToken)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"local-dev-only-not-a-secret-DO-NOT-USE-IN-PRODUCTION-0000000000"})
    void testInvalidSecretKey(String secretKey) {
        User user = UserMother.randomUser();

        Mockito.when(jwtConfiguration.getSecretKey()).thenReturn(secretKey);

        Assertions.assertThrows(InvalidSecretKeyException.class,
                () -> repository.getToken(user));
    }

    @ParameterizedTest
    @ValueSource(strings = {"foo"})
    void testWeakSecretKey(String secretKey) {
        User user = UserMother.randomUser();

        Mockito.when(jwtConfiguration.getSecretKey()).thenReturn(secretKey);

        Assertions.assertThrows(WeakSecretKeyException.class,
                () -> repository.getToken(user));
    }
    
    

    // --- isPlatformAdmin ---

    @Test
    void isPlatformAdmin_returnsTrue_whenTokenContainsPlatformAdminClaim() {
        User user = UserMother.randomUser();
        user.setPlatformAdmin(true);
        mockJwtConfig();

        Token token = repository.getToken(user);

        Assertions.assertTrue(repository.isPlatformAdmin(token));
    }

    @Test
    void isPlatformAdmin_returnsFalse_whenTokenDoesNotContainPlatformAdminClaim() {
        User user = UserMother.randomUser();
        user.setPlatformAdmin(false);
        mockJwtConfig();

        Token token = repository.getToken(user);

        Assertions.assertFalse(repository.isPlatformAdmin(token));
    }

    // --- getCommunityMemberships ---

    @Test
    void getCommunityMemberships_returnsMembershipMapFromToken() {
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        CommunityMembership membership = new CommunityMembership.Builder()
                .withId(UUID.randomUUID())
                .withUser(user)
                .withCommunity(community)
                .withRole(CommunityRole.COMMUNITY_ADMIN)
                .withEnabled(true)
                .build();
        user.setMemberships(List.of(membership));
        mockJwtConfig();

        Token token = repository.getToken(user);

        Map<String, String> memberships = repository.getCommunityMemberships(token);
        Assertions.assertTrue(memberships.containsKey(community.getId().toString()));
        Assertions.assertEquals(CommunityRole.COMMUNITY_ADMIN.name(), memberships.get(community.getId().toString()));
    }

    @Test
    void findRejectionReason_acceptsAnyToken_whenThePasswordWasNeverChanged() {
        User user = UserMother.randomUser();
        user.enable();
        user.setPasswordChangedAt(null);
        mockJwtConfig();

        Token token = repository.getToken(user);

        Assertions.assertEquals(Optional.empty(), repository.findRejectionReason(token, user));
    }

    @Test
    void findRejectionReason_acceptsATokenIssuedInTheSameSecondAsThePasswordChange() throws Exception {
        User user = UserMother.randomUser();
        user.enable();
        mockJwtConfig();
        Token token = repository.getToken(user);
        Instant issuedAt = issuedAtOf(token);

        // iat only has second precision: a change late in the same second must not reject the token
        user.setPasswordChangedAt(issuedAt.plusMillis(999));
        Assertions.assertEquals(Optional.empty(), repository.findRejectionReason(token, user));

        user.setPasswordChangedAt(issuedAt);
        Assertions.assertEquals(Optional.empty(), repository.findRejectionReason(token, user));
    }

    @Test
    void findRejectionReason_rejectsATokenIssuedInASecondBeforeThePasswordChange() throws Exception {
        User user = UserMother.randomUser();
        user.enable();
        mockJwtConfig();
        Token token = repository.getToken(user);
        Instant issuedAt = issuedAtOf(token);

        user.setPasswordChangedAt(issuedAt.plusSeconds(1));
        Assertions.assertEquals(Optional.of(TokenRejectionReason.ISSUED_BEFORE_PASSWORD_CHANGE), repository.findRejectionReason(token, user));

        user.setPasswordChangedAt(issuedAt.plusSeconds(1).plusMillis(500));
        Assertions.assertEquals(Optional.of(TokenRejectionReason.ISSUED_BEFORE_PASSWORD_CHANGE), repository.findRejectionReason(token, user));
    }

    @Test
    void findRejectionReason_acceptsATokenIssuedAfterThePasswordChange() throws Exception {
        User user = UserMother.randomUser();
        user.enable();
        mockJwtConfig();
        Token token = repository.getToken(user);

        user.setPasswordChangedAt(issuedAtOf(token).minusSeconds(5));

        Assertions.assertEquals(Optional.empty(), repository.findRejectionReason(token, user));
    }

    @Test
    void findRejectionReason_rejectsADisabledUser() {
        User user = UserMother.randomUser();
        user.enable();
        mockJwtConfig();
        Token token = repository.getToken(user);

        user.setEnabled(false);

        Assertions.assertEquals(Optional.of(TokenRejectionReason.USER_DISABLED), repository.findRejectionReason(token, user));
    }

    @Test
    void findRejectionReason_acceptsAnyToken_whenTheUserWasNeverDisabled() {
        User user = UserMother.randomUser();
        user.enable();
        user.setDisabledAt(null);
        mockJwtConfig();
        Token token = repository.getToken(user);

        Assertions.assertEquals(Optional.empty(), repository.findRejectionReason(token, user));
    }

    @Test
    void findRejectionReason_acceptsATokenIssuedInTheSameSecondAsTheLastDisable() throws Exception {
        User user = UserMother.randomUser();
        user.enable();
        mockJwtConfig();
        Token token = repository.getToken(user);
        Instant issuedAt = issuedAtOf(token);

        // iat only has second precision: a disable late in the same second must not reject the token
        user.setDisabledAt(issuedAt.plusMillis(999));
        Assertions.assertEquals(Optional.empty(), repository.findRejectionReason(token, user));

        user.setDisabledAt(issuedAt);
        Assertions.assertEquals(Optional.empty(), repository.findRejectionReason(token, user));
    }

    @Test
    void findRejectionReason_rejectsATokenIssuedInASecondBeforeTheLastDisable() throws Exception {
        User user = UserMother.randomUser();
        user.enable();
        mockJwtConfig();
        Token token = repository.getToken(user);
        Instant issuedAt = issuedAtOf(token);

        user.setDisabledAt(issuedAt.plusSeconds(1));
        Assertions.assertEquals(Optional.of(TokenRejectionReason.ISSUED_BEFORE_DISABLE), repository.findRejectionReason(token, user));

        user.setDisabledAt(issuedAt.plusSeconds(1).plusMillis(500));
        Assertions.assertEquals(Optional.of(TokenRejectionReason.ISSUED_BEFORE_DISABLE), repository.findRejectionReason(token, user));
    }

    @Test
    void findRejectionReason_withBothTimestamps_acceptsOnlyATokenIssuedNoEarlierThanEither() throws Exception {
        User user = UserMother.randomUser();
        user.enable();
        mockJwtConfig();
        Token token = repository.getToken(user);
        Instant issuedAt = issuedAtOf(token);

        user.setPasswordChangedAt(issuedAt.minusSeconds(5));
        user.setDisabledAt(issuedAt.minusSeconds(3));
        Assertions.assertEquals(Optional.empty(), repository.findRejectionReason(token, user));

        user.setPasswordChangedAt(issuedAt.minusSeconds(5));
        user.setDisabledAt(issuedAt.plusSeconds(1));
        Assertions.assertEquals(Optional.of(TokenRejectionReason.ISSUED_BEFORE_DISABLE), repository.findRejectionReason(token, user));

        user.setPasswordChangedAt(issuedAt.plusSeconds(1));
        user.setDisabledAt(issuedAt.minusSeconds(3));
        Assertions.assertEquals(Optional.of(TokenRejectionReason.ISSUED_BEFORE_PASSWORD_CHANGE), repository.findRejectionReason(token, user));

        user.setPasswordChangedAt(issuedAt.plusSeconds(2));
        user.setDisabledAt(issuedAt.plusSeconds(1));
        Assertions.assertEquals(Optional.of(TokenRejectionReason.ISSUED_BEFORE_PASSWORD_CHANGE), repository.findRejectionReason(token, user));
    }

    // --- verify (#347) ---

    private static final UUID SUBJECT = UUID.randomUUID();

    static Stream<Arguments> tokensRejectedWithoutATrustedSubject() {
        CraftedTokens tokens = new CraftedTokens(SECRET_KEY);
        Instant now = Instant.now();
        return Stream.of(
                Arguments.of("tampered", tokens.tampered(SUBJECT), TokenRejectionReason.INVALID_SIGNATURE),
                Arguments.of("other key", tokens.signedWithOtherKey(SUBJECT), TokenRejectionReason.INVALID_SIGNATURE),
                Arguments.of("alg none", tokens.unsigned(SUBJECT), TokenRejectionReason.UNSUPPORTED),
                Arguments.of("expired and unsigned", tokens.expiredUnsigned(SUBJECT), TokenRejectionReason.UNSUPPORTED),
                Arguments.of("RS256", tokens.signedWithRsa(SUBJECT), TokenRejectionReason.UNSUPPORTED),
                Arguments.of("HS512", tokens.signedWithHs512(SUBJECT), TokenRejectionReason.UNSUPPORTED),
                // jjwt throws the same exception for an unknown algorithm name as for a signature that does not match
                Arguments.of("unknown alg", tokens.unknownAlgorithm(SUBJECT), TokenRejectionReason.INVALID_SIGNATURE),
                Arguments.of("header not JSON", tokens.headerNotJson(SUBJECT), TokenRejectionReason.MALFORMED),
                Arguments.of("no dots", "not-a-jwt", TokenRejectionReason.MALFORMED),
                Arguments.of("bad base64", "!!!.!!!.!!!", TokenRejectionReason.MALFORMED),
                Arguments.of("empty", "", TokenRejectionReason.MALFORMED),
                Arguments.of("no sub", tokens.signed(b -> b.setId(UUID.randomUUID().toString())
                        .setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(60)))),
                        TokenRejectionReason.MISSING_CLAIMS),
                Arguments.of("no jti", tokens.signed(b -> b.setSubject(SUBJECT.toString())
                        .setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(60)))),
                        TokenRejectionReason.MISSING_CLAIMS),
                Arguments.of("no exp", tokens.signed(b -> b.setSubject(SUBJECT.toString())
                        .setId(UUID.randomUUID().toString()).setIssuedAt(Date.from(now))),
                        TokenRejectionReason.MISSING_CLAIMS),
                Arguments.of("sub not a UUID", tokens.signed(b -> b.setSubject("not-a-user-id")
                        .setId(UUID.randomUUID().toString()).setIssuedAt(Date.from(now))
                        .setExpiration(Date.from(now.plusSeconds(60)))),
                        TokenRejectionReason.INVALID_CLAIMS),
                Arguments.of("not yet valid", tokens.signed(b -> b.setSubject(SUBJECT.toString())
                        .setId(UUID.randomUUID().toString()).setIssuedAt(Date.from(now))
                        .setNotBefore(Date.from(now.plusSeconds(3600)))
                        .setExpiration(Date.from(now.plusSeconds(7200)))),
                        TokenRejectionReason.INVALID_CLAIMS)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tokensRejectedWithoutATrustedSubject")
    void verify_rejectsAnUnverifiableToken_withItsReason_andNoSubject(String name, String token,
                                                                        TokenRejectionReason reason) {
        Mockito.when(jwtConfiguration.getSecretKey()).thenReturn(SECRET_KEY);

        InvalidTokenException exception = Assertions.assertThrows(InvalidTokenException.class,
                () -> repository.verify(Token.of(token)));

        Assertions.assertEquals(reason, exception.getReason());
        Assertions.assertTrue(exception.getUserId().isEmpty());
        Assertions.assertNull(exception.getCause());
        for (String segment : token.split("\\.", -1)) {
            if (!segment.isEmpty()) {
                Assertions.assertFalse(exception.getMessage().contains(segment), exception::getMessage);
            }
        }
    }

    @Test
    void verify_rejectsAnExpiredToken_withItsVerifiedSubject() {
        Mockito.when(jwtConfiguration.getSecretKey()).thenReturn(SECRET_KEY);
        String expired = new CraftedTokens(SECRET_KEY).expired(SUBJECT);

        InvalidTokenException exception = Assertions.assertThrows(InvalidTokenException.class,
                () -> repository.verify(Token.of(expired)));

        Assertions.assertEquals(TokenRejectionReason.EXPIRED, exception.getReason());
        Assertions.assertEquals(Optional.of(SUBJECT), exception.getUserId());
    }

    @Test
    void verify_returnsTheSubjectAndTheIdOfAValidToken() {
        User user = UserMother.randomUser();
        mockJwtConfig();
        Token token = repository.getToken(user);

        VerifiedToken verified = repository.verify(token);

        Assertions.assertEquals(user.getId(), verified.userId());
        Assertions.assertEquals(repository.getJtiFromToken(token).orElseThrow(), verified.jti());
    }

    @Test
    void verify_leavesAMissingKey_toFailAsAConfigurationError() {
        Mockito.when(jwtConfiguration.getSecretKey()).thenReturn(null);

        Assertions.assertThrows(SecretKeyNotFoundException.class,
                () -> repository.verify(Token.of(new CraftedTokens(SECRET_KEY).valid(SUBJECT))));
    }

    @Test
    void findRejectionReason_rejectsATokenCheckedAgainstAnotherUser() {
        User user = UserMother.randomUser();
        user.enable();
        mockJwtConfig();
        Token token = repository.getToken(user);

        User someoneElse = UserMother.randomUser();
        someoneElse.enable();

        Assertions.assertEquals(Optional.of(TokenRejectionReason.SUBJECT_MISMATCH),
                repository.findRejectionReason(token, someoneElse));
    }

    /**
     * Reads the {@code iat} claim straight from the token's payload, independently of the code under test.
     */
    private static Instant issuedAtOf(Token token) throws Exception {
        String payload = token.getToken().split("\\.")[1];
        JsonNode claims = new ObjectMapper().readTree(Base64.getUrlDecoder().decode(payload));
        return Instant.ofEpochSecond(claims.get("iat").asLong());
    }

    private void mockJwtConfig() {
        // Mock expiration time and JWT secret key
        Mockito.when(jwtConfiguration.getExpirationTime()).thenReturn(30);
        Mockito.when(jwtConfiguration.getSecretKey()).thenReturn(JwtSecretKeyGenerator.generate());
    }
}
