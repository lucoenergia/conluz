package org.lucoenergia.conluz.infrastructure.admin.user.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.auth.*;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.AuthenticationThrottleService;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.LoginAttempt;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.LoginFailureReason;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.TooManyFailedAttemptsException;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.AuthenticationThrottleServiceImpl;
import org.mockito.Mockito;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private final LoginAttempt attempt = Mockito.mock(LoginAttempt.class);

    @BeforeEach
    void admitLogins() {
        when(authenticationThrottleService.startLogin("12.345.678-a", CLIENT_IP)).thenReturn(attempt);
    }

    @Test
    void login_whenThrottled_isRefusedBeforeThePasswordIsChecked() {
        doThrow(new TooManyFailedAttemptsException(300))
                .when(authenticationThrottleService).startLogin("12.345.678-a", CLIENT_IP);

        assertThrows(TooManyFailedAttemptsException.class, () -> authService.login(credentials, CLIENT_IP));

        verifyNoInteractions(authenticator);
    }

    @Test
    void login_withBadCredentials_isCountedAsAFailure_andStillRejected() {
        doThrow(new BadCredentialsException("Bad credentials")).when(authenticator).authenticate(credentials);

        assertThrows(BadCredentialsException.class, () -> authService.login(credentials, CLIENT_IP));

        verify(attempt).failed(LoginFailureReason.BAD_CREDENTIALS);
        verify(attempt, never()).succeeded();
        verify(attempt).close();
    }

    @Test
    void login_ofADisabledAccount_isCountedAsAFailure_andStillRejected() {
        doThrow(new DisabledException("User is disabled")).when(authenticator).authenticate(credentials);

        assertThrows(DisabledException.class, () -> authService.login(credentials, CLIENT_IP));

        verify(attempt).failed(LoginFailureReason.DISABLED);
        verify(attempt).close();
    }

    @Test
    void login_failingForAnotherReason_isNotCounted_andReleasesItsSlot() {
        doThrow(new InternalAuthenticationServiceException("database unavailable"))
                .when(authenticator).authenticate(credentials);

        assertThrows(InternalAuthenticationServiceException.class, () -> authService.login(credentials, CLIENT_IP));

        verify(attempt, never()).failed(any());
        verify(attempt, never()).succeeded();
        verify(attempt).close();
    }

    @Test
    void login_thatSucceeds_resetsTheAccountCounter() {
        User user = UserMother.randomUser();
        Token token = Token.of("issued-token");
        when(getUserRepository.findByPersonalId(any())).thenReturn(Optional.of(user));
        when(authRepository.getToken(user)).thenReturn(token);

        assertEquals(token, authService.login(credentials, CLIENT_IP));

        verify(attempt).succeeded();
        verify(attempt, never()).failed(any());
        verify(attempt).close();
    }

    @Test
    void concurrentLoginsOnOneAccount_checkNoMorePasswordsThanTheAccountLimit() throws Exception {
        int requests = 10;
        assertEquals(5, passwordsCheckedByConcurrentFailedLogins(requests,
                i -> new Credentials("12.345.678-a", "guess " + i), i -> "192.0.2." + i));
    }

    @Test
    void concurrentLoginsFromOneAddress_checkNoMorePasswordsThanTheAddressLimit() throws Exception {
        int requests = 30;
        assertEquals(20, passwordsCheckedByConcurrentFailedLogins(requests,
                i -> new Credentials("ACCOUNT" + i, "guess"), i -> CLIENT_IP));
    }

    /**
     * Sends failed logins in parallel, with every password check held open until all of them have either reached
     * the check or been refused, which is the widest burst a client can produce.
     *
     * @return how many passwords were checked
     */
    private int passwordsCheckedByConcurrentFailedLogins(int requests, IntFunction<Credentials> credentialsOf,
                                                        IntFunction<String> clientIpOf) throws Exception {
        CountDownLatch arrived = new CountDownLatch(requests);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger passwordsChecked = new AtomicInteger();
        Authenticator blockingAuthenticator = given -> {
            passwordsChecked.incrementAndGet();
            arrived.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            throw new BadCredentialsException("Bad credentials");
        };
        AuthService throttled = new AuthServiceImpl(blockingAuthenticator, getUserRepository, authRepository,
                blacklistedTokenRepository, new AuthenticationThrottleServiceImpl(Clock.systemUTC()));
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        try {
            List<Future<?>> outcomes = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                Credentials given = credentialsOf.apply(i);
                String clientIp = clientIpOf.apply(i);
                outcomes.add(pool.submit(() -> {
                    try {
                        throttled.login(given, clientIp);
                    } catch (TooManyFailedAttemptsException e) {
                        arrived.countDown();
                    } catch (BadCredentialsException e) {
                        // Counted once its password was checked
                    }
                }));
            }

            assertTrue(arrived.await(10, TimeUnit.SECONDS));
            int checked = passwordsChecked.get();
            release.countDown();
            for (Future<?> outcome : outcomes) {
                outcome.get(10, TimeUnit.SECONDS);
            }
            return checked;
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
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