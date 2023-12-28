package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        InvalidTokenException exception = Assertions.assertThrows(InvalidTokenException.class,
                () -> repository.getUserIdFromToken(Token.of(invalidToken)));

        Assertions.assertEquals(invalidToken, exception.getToken());
    }

    private void mockJwtConfig() {
        // Mock expiration time and JWT secret key
        Mockito.when(jwtConfiguration.getExpirationTime()).thenReturn(30);
        Mockito.when(jwtConfiguration.getSecretKey()).thenReturn(JwtSecretKeyGenerator.generate());
    }
}
