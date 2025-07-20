package org.lucoenergia.conluz.infrastructure.admin.user.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.*;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private final Authenticator authenticator = Mockito.mock(Authenticator.class);
    private final GetUserRepository getUserRepository = Mockito.mock(GetUserRepository.class);
    private final AuthRepository authRepository = Mockito.mock(AuthRepository.class);
    private final BlacklistedTokenRepository blacklistedTokenRepository = Mockito.mock(BlacklistedTokenRepository.class);

    private final AuthService authService = new AuthServiceImpl(authenticator, getUserRepository, authRepository,
            blacklistedTokenRepository);

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

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

    @Test
    void getCurrentUser_shouldReturnAuthenticatedUser() {
        // Given
        User user = mock(User.class);
        Authentication authentication = mock(Authentication.class);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(authentication.getPrincipal()).thenReturn(user);

        // When
        User result = authService.getCurrentUser();

        // Then
        assertTrue(result == user);
    }

    @Test
    void getCurrentUser_shouldThrowClassCastExceptionWhenPrincipalIsNotUser() {
        // Given
        String principal = "Not a User instance";
        Authentication authentication = mock(Authentication.class);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(authentication.getPrincipal()).thenReturn(principal);

        // When / Then
        assertThrows(ClassCastException.class, authService::getCurrentUser);
    }
}