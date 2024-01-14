package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.Token;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.shared.security.JwtSecretKeyGenerator;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JwtAuthRepositoryTest {

    private final static String SECRET_KEY = "b5f86373ba5d7593f4c6eab57862bf4be76369c1adbe263ae2d50ddae40b8ca2";

    @InjectMocks
    private JwtAuthRepository repository;

    @Mock
    private JwtConfiguration jwtConfiguration;

    @Test
    void testGetAValidToken() {
        User user = UserMother.randomUser();

        mockJwtConfig();

        Token token = repository.getToken(user);
        Assertions.assertNotNull(token);

        Assertions.assertTrue(repository.isTokenValid(token, user));
    }

    @Test
    void testTokenClaims() {
        User user = UserMother.randomUser();

        mockJwtConfig();

        Token token = repository.getToken(user);

        Assertions.assertNotNull(token);
        Assertions.assertTrue(repository.isTokenValid(token, user));
        Assertions.assertEquals(user.getId(), repository.getUserIdFromToken(token));
        Assertions.assertEquals(user.getRole().name(), repository.getRole(token));
    }

    @Test
    void testGetUserIdByInvalidToken() {
        String invalidToken = "invalid-token";

        Mockito.when(jwtConfiguration.getSecretKey()).thenReturn(SECRET_KEY);

        InvalidTokenException exception = Assertions.assertThrows(InvalidTokenException.class,
                () -> repository.getUserIdFromToken(Token.of(invalidToken)));

        Assertions.assertEquals(invalidToken, exception.getToken());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @NullSource
    void testMissingSecretKey(String secretKey) {
        String invalidToken = "invalid-token";

        Mockito.when(jwtConfiguration.getSecretKey()).thenReturn(secretKey);

        Assertions.assertThrows(SecretKeyNotFoundException.class,
                () -> repository.getUserIdFromToken(Token.of(invalidToken)));
    }

    private void mockJwtConfig() {
        // Mock expiration time and JWT secret key
        Mockito.when(jwtConfiguration.getExpirationTime()).thenReturn(30);
        Mockito.when(jwtConfiguration.getSecretKey()).thenReturn(JwtSecretKeyGenerator.generate());
    }
}
