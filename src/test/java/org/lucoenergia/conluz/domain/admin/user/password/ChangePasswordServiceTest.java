package org.lucoenergia.conluz.domain.admin.user.password;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.lucoenergia.conluz.domain.admin.user.auth.Token;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.AuthenticationThrottleService;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.PasswordChangeAttempt;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.TooManyFailedAttemptsException;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.AuthenticationThrottleServiceImpl;
import org.lucoenergia.conluz.infrastructure.admin.user.password.ChangePasswordServiceImpl;
import org.lucoenergia.conluz.infrastructure.admin.user.password.UserPasswordEncoder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangePasswordServiceTest {

    private static final String CURRENT_PASSWORD = "the current password of the user";
    private static final String NEW_PASSWORD = "a brand new password for this user";
    private static final String CLIENT_IP = "203.0.113.7";

    private final BCryptPasswordEncoder bcrypt = spy(new BCryptPasswordEncoder(4));
    private final Token usedToken = Token.of("the.used.token");

    @Mock
    private GetUserRepository getUserRepository;
    @Mock
    private ChangePasswordRepository changePasswordRepository;
    @Mock
    private AuthService authService;
    @Mock
    private AuthenticationThrottleService authenticationThrottleService;
    @Mock
    private PasswordChangeAttempt attempt;

    private User user;
    private ChangePasswordService service;

    @BeforeEach
    void setUp() {
        user = UserMother.randomUser();
        user.setPassword(bcrypt.encode(CURRENT_PASSWORD));
        when(getUserRepository.findById(argThat(id -> id.getId().equals(user.getId())))).thenReturn(Optional.of(user));
        lenient().when(authenticationThrottleService.startPasswordChange(user, CLIENT_IP)).thenReturn(attempt);
        service = new ChangePasswordServiceImpl(getUserRepository, changePasswordRepository,
                new UserPasswordEncoder(bcrypt), authService, authenticationThrottleService);
    }

    @Test
    void storesTheHashOfTheNewPassword_andRevokesTheUsedToken() {
        Instant before = Instant.now();

        service.changePassword(UserId.of(user.getId()), CURRENT_PASSWORD, NEW_PASSWORD, usedToken, CLIENT_IP);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Instant> changedAt = ArgumentCaptor.forClass(Instant.class);
        verify(changePasswordRepository).changePassword(argThat(id -> id.getId().equals(user.getId())), hash.capture(),
                changedAt.capture());
        assertTrue(bcrypt.matches(NEW_PASSWORD, hash.getValue()));
        assertFalse(changedAt.getValue().isBefore(before));
        verify(authService).blacklistToken(usedToken);
        verify(attempt).succeeded();
        verify(attempt).close();
    }

    @Test
    void aWrongCurrentPassword_changesNothing_andRevokesNothing() {
        UserId userId = UserId.of(user.getId());

        assertThrows(IncorrectCurrentPasswordException.class,
                () -> service.changePassword(userId, "not the current password", NEW_PASSWORD, usedToken, CLIENT_IP));

        verifyNoInteractions(changePasswordRepository, authService);
        verify(attempt).failed();
        verify(attempt, never()).succeeded();
        verify(attempt).close();
    }

    @Test
    void aNewPasswordThatBreaksThePolicy_changesNothing_andRevokesNothing() {
        UserId userId = UserId.of(user.getId());

        PasswordPolicyViolationException e = assertThrows(PasswordPolicyViolationException.class,
                () -> service.changePassword(userId, CURRENT_PASSWORD, "too short", usedToken, CLIENT_IP));

        assertEquals(PasswordPolicyViolation.TOO_SHORT, e.getRule());
        verify(changePasswordRepository, never()).changePassword(any(), any(), any());
        verifyNoInteractions(authService);
        // The current password was right: neither a failure nor a success
        verify(attempt, never()).failed();
        verify(attempt, never()).succeeded();
        verify(attempt).close();
    }

    @Test
    void aNewPasswordEqualToTheCurrentOne_isRefused_changesNothing_andRevokesNothing() {
        UserId userId = UserId.of(user.getId());
        // An equal value, not the same instance
        String samePassword = new String(CURRENT_PASSWORD.toCharArray());
        clearInvocations(bcrypt);

        assertThrows(PasswordUnchangedException.class,
                () -> service.changePassword(userId, CURRENT_PASSWORD, samePassword, usedToken, CLIENT_IP));

        verify(bcrypt, never()).encode(any());
        verifyNoInteractions(changePasswordRepository, authService);
        // The current password was right: neither a failure nor a success
        verify(attempt, never()).failed();
        verify(attempt, never()).succeeded();
        verify(attempt).close();
    }

    @Test
    void aWrongCurrentPassword_equalToTheNewOne_isReportedAsWrong_andCountedAsAFailure() {
        UserId userId = UserId.of(user.getId());

        assertThrows(IncorrectCurrentPasswordException.class, () -> service.changePassword(userId,
                "not the current password", "not the current password", usedToken, CLIENT_IP));

        verifyNoInteractions(changePasswordRepository, authService);
        verify(attempt).failed();
        verify(attempt, never()).succeeded();
        verify(attempt).close();
    }

    @Test
    void aThrottledChange_isRefusedBeforeTheCurrentPasswordIsChecked_andChangesNothing() {
        UserId userId = UserId.of(user.getId());
        doThrow(new TooManyFailedAttemptsException(600))
                .when(authenticationThrottleService).startPasswordChange(user, CLIENT_IP);

        assertThrows(TooManyFailedAttemptsException.class,
                () -> service.changePassword(userId, CURRENT_PASSWORD, NEW_PASSWORD, usedToken, CLIENT_IP));

        verify(bcrypt, never()).matches(any(), any());
        verifyNoInteractions(changePasswordRepository, authService);
    }

    @Test
    void concurrentChangesWithWrongCurrentPasswords_checkNoMorePasswordsThanTheAccountLimit() throws Exception {
        int requests = 10;
        CountDownLatch arrived = new CountDownLatch(requests);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger passwordsChecked = new AtomicInteger();
        PasswordEncoder blockingEncoder = new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return bcrypt.encode(rawPassword);
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                passwordsChecked.incrementAndGet();
                arrived.countDown();
                await(release);
                return false;
            }
        };
        ChangePasswordService throttled = new ChangePasswordServiceImpl(getUserRepository, changePasswordRepository,
                new UserPasswordEncoder(blockingEncoder), authService,
                new AuthenticationThrottleServiceImpl(Clock.systemUTC()));
        UserId userId = UserId.of(user.getId());
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        try {
            List<Future<Class<?>>> outcomes = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                String clientIp = "192.0.2." + i;
                outcomes.add(pool.submit(() -> {
                    try {
                        throttled.changePassword(userId, "not the current password", NEW_PASSWORD, usedToken, clientIp);
                        return Void.class;
                    } catch (TooManyFailedAttemptsException e) {
                        arrived.countDown();
                        return e.getClass();
                    } catch (IncorrectCurrentPasswordException e) {
                        return e.getClass();
                    }
                }));
            }

            // Every request has either reached the password check or been refused, and none has failed yet
            assertTrue(arrived.await(10, TimeUnit.SECONDS));
            assertEquals(5, passwordsChecked.get());
            release.countDown();

            long refused = 0;
            for (Future<Class<?>> outcome : outcomes) {
                if (outcome.get(10, TimeUnit.SECONDS) == TooManyFailedAttemptsException.class) {
                    refused++;
                }
            }
            assertEquals(5, refused);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
