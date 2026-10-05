package org.lucoenergia.conluz.infrastructure.admin.user.auth;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.auth.*;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.AuthenticationThrottleService;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.LoginFailureReason;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.TooManyFailedAttemptsException;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.mockito.Mockito;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class AuthServiceImplTest {

    private final Authenticator authenticator = Mockito.mock(Authenticator.class);
    private final GetUserRepository getUserRepository = Mockito.mock(GetUserRepository.class);
    private final AuthRepository authRepository = Mockito.mock(AuthRepository.class);
    private final BlacklistedTokenRepository blacklistedTokenRepository = Mockito.mock(BlacklistedTokenRepository.class);

    private final AuthenticationThrottleService authenticationThrottleService =
            Mockito.mock(AuthenticationThrottleService.class);

    private final AuthServiceImpl authService = new AuthServiceImpl(authenticator, getUserRepository, authRepository,
            blacklistedTokenRepository, authenticationThrottleService);

    private static final String CLIENT_IP = "203.0.113.7";
    private final Credentials credentials = new Credentials("12.345.678-a", "a password");

    @Test
    void login_whenThrottled_isRefusedBeforeThePasswordIsChecked() {
        doThrow(new TooManyFailedAttemptsException(300))
                .when(authenticationThrottleService).checkLogin("12.345.678-a", CLIENT_IP);

        assertThrows(TooManyFailedAttemptsException.class, () -> authService.login(credentials, CLIENT_IP));

        verifyNoInteractions(authenticator);
    }

    @Test
    void login_withBadCredentials_isCountedAsAFailure_andStillRejected() {
        doThrow(new BadCredentialsException("Bad credentials")).when(authenticator).authenticate(credentials);

        assertThrows(BadCredentialsException.class, () -> authService.login(credentials, CLIENT_IP));

        verify(authenticationThrottleService).loginFailed("12.345.678-a", CLIENT_IP, LoginFailureReason.BAD_CREDENTIALS);
        verify(authenticationThrottleService, never()).loginSucceeded(any());
    }

    @Test
    void login_ofADisabledAccount_isCountedAsAFailure_andStillRejected() {
        doThrow(new DisabledException("User is disabled")).when(authenticator).authenticate(credentials);

        assertThrows(DisabledException.class, () -> authService.login(credentials, CLIENT_IP));

        verify(authenticationThrottleService).loginFailed("12.345.678-a", CLIENT_IP, LoginFailureReason.DISABLED);
    }

    @Test
    void login_failingForAnotherReason_isNotCountedAsAFailedAttempt() {
        doThrow(new InternalAuthenticationServiceException("database unavailable"))
                .when(authenticator).authenticate(credentials);

        assertThrows(InternalAuthenticationServiceException.class, () -> authService.login(credentials, CLIENT_IP));

        verify(authenticationThrottleService, never()).loginFailed(any(), any(), any());
        verify(authenticationThrottleService, never()).loginSucceeded(any());
    }

    @Test
    void login_thatSucceeds_resetsTheAccountCounter() {
        User user = UserMother.randomUser();
        Token token = Token.of("issued-token");
        when(getUserRepository.findByPersonalId(any())).thenReturn(Optional.of(user));
        when(authRepository.getToken(user)).thenReturn(token);

        assertEquals(token, authService.login(credentials, CLIENT_IP));

        verify(authenticationThrottleService).loginSucceeded("12.345.678-a");
        verify(authenticationThrottleService, never()).loginFailed(any(), any(), any());
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
}