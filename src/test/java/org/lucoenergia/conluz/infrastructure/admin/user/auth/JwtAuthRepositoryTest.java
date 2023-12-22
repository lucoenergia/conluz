package org.lucoenergia.conluz.infrastructure.admin.user.auth;

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

        Mockito.when(jwtConfiguration.getExpirationTime()).thenReturn(30);
        Mockito.when(jwtConfiguration.getSecretKey()).thenReturn(JwtSecretKeyGenerator.generate());

        Token token = repository.getToken(user);
        Assertions.assertNotNull(token);

        Assertions.assertTrue(repository.isTokenValid(token, user));
    }
}
