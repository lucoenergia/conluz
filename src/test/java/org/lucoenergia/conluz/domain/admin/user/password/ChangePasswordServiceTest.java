package org.lucoenergia.conluz.domain.admin.user.password;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.lucoenergia.conluz.domain.admin.user.auth.Token;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.AuthenticationThrottleService;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.TooManyFailedAttemptsException;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.password.ChangePasswordServiceImpl;
import org.lucoenergia.conluz.infrastructure.admin.user.password.UserPasswordEncoder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
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

    private User user;
    private ChangePasswordService service;

    @BeforeEach
    void setUp() {
        user = UserMother.randomUser();
        user.setPassword(bcrypt.encode(CURRENT_PASSWORD));
        when(getUserRepository.findById(argThat(id -> id.getId().equals(user.getId())))).thenReturn(Optional.of(user));
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
        verify(authenticationThrottleService).passwordChangeSucceeded(user);
    }

    @Test
    void aWrongCurrentPassword_changesNothing_andRevokesNothing() {
        UserId userId = UserId.of(user.getId());

        assertThrows(IncorrectCurrentPasswordException.class,
                () -> service.changePassword(userId, "not the current password", NEW_PASSWORD, usedToken, CLIENT_IP));

        verifyNoInteractions(changePasswordRepository, authService);
        verify(authenticationThrottleService).passwordChangeFailed(user, CLIENT_IP);
        verify(authenticationThrottleService, never()).passwordChangeSucceeded(any());
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
        verify(authenticationThrottleService, never()).passwordChangeFailed(any(), anyString());
        verify(authenticationThrottleService, never()).passwordChangeSucceeded(any());
    }

    @Test
    void aThrottledChange_isRefusedBeforeTheCurrentPasswordIsChecked_andChangesNothing() {
        UserId userId = UserId.of(user.getId());
        doThrow(new TooManyFailedAttemptsException(600))
                .when(authenticationThrottleService).checkPasswordChange(user, CLIENT_IP);

        assertThrows(TooManyFailedAttemptsException.class,
                () -> service.changePassword(userId, CURRENT_PASSWORD, NEW_PASSWORD, usedToken, CLIENT_IP));

        verify(bcrypt, never()).matches(any(), any());
        verifyNoInteractions(changePasswordRepository, authService);
    }
}
