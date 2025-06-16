package org.lucoenergia.conluz.infrastructure.admin.user.auth;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.auth.*;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class AuthServiceImplTest {

    private final Authenticator authenticator = Mockito.mock(Authenticator.class);
    private final GetUserRepository getUserRepository = Mockito.mock(GetUserRepository.class);
    private final AuthRepository authRepository = Mockito.mock(AuthRepository.class);
    private final BlacklistedTokenRepository blacklistedTokenRepository = Mockito.mock(BlacklistedTokenRepository.class);

    private final AuthServiceImpl authService = new AuthServiceImpl(authenticator, getUserRepository, authRepository, blacklistedTokenRepository);

    @Test
    void blacklistToken_shouldSaveTokenToBlacklist() {
        // Given
        String jti = "test-jti";
        Date expirationDate = Date.from(Instant.now().plusSeconds(3600));
        Token token = Token.of("test-token");
        
        when(authRepository.getJtiFromToken(token)).thenReturn(Optional.of(jti));
        when(authRepository.getExpirationDate(token)).thenReturn(expirationDate);
        
        // When
        boolean result = authService.blacklistToken(token);
        
        // Then
        verify(blacklistedTokenRepository).save(any(BlacklistedToken.class));
        assertTrue(result);
    }

    @Test
    void blacklistToken_shouldReturnFalseWhenJtiIsMissing() {
        // Given
        Token token = Token.of("test-token");
        Date expirationDate = Date.from(Instant.now().plusSeconds(3600));
        
        when(authRepository.getJtiFromToken(token)).thenReturn(Optional.empty());
        when(authRepository.getExpirationDate(token)).thenReturn(expirationDate);
        
        // When
        boolean result = authService.blacklistToken(token);
        
        // Then
        verify(blacklistedTokenRepository, never()).save(any(BlacklistedToken.class));
        assertFalse(result);
    }

    @Test
    void blacklistToken_shouldReturnFalseWhenExpirationDateIsNull() {
        // Given
        String jti = "test-jti";
        Token token = Token.of("test-token");
        
        when(authRepository.getJtiFromToken(token)).thenReturn(Optional.of(jti));
        when(authRepository.getExpirationDate(token)).thenReturn(null);
        
        // When
        boolean result = authService.blacklistToken(token);
        
        // Then
        verify(blacklistedTokenRepository, never()).save(any(BlacklistedToken.class));
        assertFalse(result);
    }
}