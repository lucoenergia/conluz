package org.lucoenergia.conluz.domain.admin.user.auth.throttle;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.AuthenticationThrottleServiceImpl;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.lucoenergia.conluz.infrastructure.shared.time.MutableClock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticationThrottleServiceTest {

    private static final String PERSONAL_ID = "12345678A";
    private static final String IP = "203.0.113.7";
    private static final String OTHER_IP = "198.51.100.9";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final AuthenticationThrottleService service = new AuthenticationThrottleServiceImpl(clock);
    private final LogCapture logs = LogCapture.of(AuthenticationThrottleServiceImpl.class);

    @AfterEach
    void tearDown() {
        logs.close();
    }

    @Test
    void fourFailedLogins_doNotThrottleTheAccount() {
        failLogins(PERSONAL_ID, IP, 4);

        assertDoesNotThrow(() -> service.checkLogin(PERSONAL_ID, OTHER_IP));
    }

    @Test
    void fiveFailedLogins_throttleTheAccountFromAnyAddress_forTheWholeWindow() {
        failLogins(PERSONAL_ID, IP, 5);

        assertEquals(900, retryAfter(() -> service.checkLogin(PERSONAL_ID, OTHER_IP)));
    }

    @Test
    void theWindowStartsAtTheFirstFailure_andTheAccountIsReleasedWhenItEnds() {
        service.loginFailed(PERSONAL_ID, IP, LoginFailureReason.BAD_CREDENTIALS);
        clock.advance(Duration.ofMinutes(10));
        failLogins(PERSONAL_ID, IP, 4);

        assertEquals(300, retryAfter(() -> service.checkLogin(PERSONAL_ID, IP)));

        clock.advance(Duration.ofMinutes(5).minusNanos(1));
        assertEquals(1, retryAfter(() -> service.checkLogin(PERSONAL_ID, IP)));

        clock.advance(Duration.ofNanos(1));
        assertDoesNotThrow(() -> service.checkLogin(PERSONAL_ID, IP));
    }

    @Test
    void retryAfterIsTheRemainingTimeRoundedUpToWholeSeconds() {
        failLogins(PERSONAL_ID, IP, 5);

        clock.advance(Duration.ofMillis(1_500));

        assertEquals(899, retryAfter(() -> service.checkLogin(PERSONAL_ID, IP)));
    }

    @Test
    void typingVariantsOfOnePersonalId_countAgainstTheSameAccount() {
        service.loginFailed("12345678a", IP, LoginFailureReason.BAD_CREDENTIALS);
        service.loginFailed(" 12345678 A ", IP, LoginFailureReason.BAD_CREDENTIALS);
        service.loginFailed("12345678-A", IP, LoginFailureReason.BAD_CREDENTIALS);
        service.loginFailed("12.345.678-A", IP, LoginFailureReason.BAD_CREDENTIALS);
        service.loginFailed("12345678A", IP, LoginFailureReason.DISABLED);

        assertEquals(900, retryAfter(() -> service.checkLogin("12.345.678-a", OTHER_IP)));
    }

    @Test
    void failedLoginsAndWrongCurrentPasswords_shareTheAccountCounter() {
        User user = UserMother.randomUserWithPersonalId(PERSONAL_ID);
        failLogins("12.345.678-a", IP, 3);
        service.passwordChangeFailed(user, IP);
        service.passwordChangeFailed(user, IP);

        assertEquals(900, retryAfter(() -> service.checkLogin(PERSONAL_ID, OTHER_IP)));
        assertEquals(900, retryAfter(() -> service.checkPasswordChange(user, OTHER_IP)));
    }

    @Test
    void twentyFailuresFromOneAddress_throttleThatAddressForEveryAccount_onBothEndpoints() {
        for (int i = 0; i < 19; i++) {
            service.loginFailed("ACCOUNT" + i, IP, LoginFailureReason.BAD_CREDENTIALS);
        }
        assertDoesNotThrow(() -> service.checkLogin(PERSONAL_ID, IP));

        service.passwordChangeFailed(UserMother.randomUser(), IP);

        assertEquals(900, retryAfter(() -> service.checkLogin(PERSONAL_ID, IP)));
        assertEquals(900, retryAfter(() -> service.checkPasswordChange(UserMother.randomUser(), IP)));
        assertDoesNotThrow(() -> service.checkLogin(PERSONAL_ID, OTHER_IP));
    }

    @Test
    void aSuccessfulLogin_resetsTheAccountCounter_butNeverTheAddressCounter() {
        failLogins(PERSONAL_ID, IP, 4);

        service.loginSucceeded("12.345.678-a");

        failLogins(PERSONAL_ID, IP, 4);
        assertDoesNotThrow(() -> service.checkLogin(PERSONAL_ID, OTHER_IP));
        // 8 failures from the address so far; 12 more on other accounts reach its limit of 20
        for (int i = 0; i < 12; i++) {
            service.loginFailed("ACCOUNT" + i, IP, LoginFailureReason.BAD_CREDENTIALS);
        }
        assertEquals(900, retryAfter(() -> service.checkLogin("ANOTHERACCOUNT", IP)));
    }

    @Test
    void aSuccessfulPasswordChange_resetsTheAccountCounter() {
        User user = UserMother.randomUserWithPersonalId(PERSONAL_ID);
        failLogins(PERSONAL_ID, IP, 4);

        service.passwordChangeSucceeded(user);

        failLogins(PERSONAL_ID, OTHER_IP, 4);
        assertDoesNotThrow(() -> service.checkLogin(PERSONAL_ID, "192.0.2.1"));
    }

    @Test
    void whenBothCountersAreOverTheLimit_theLongerWaitIsGiven() {
        failLogins(PERSONAL_ID, OTHER_IP, 5);
        clock.advance(Duration.ofMinutes(5));
        for (int i = 0; i < 20; i++) {
            service.loginFailed("ACCOUNT" + i, IP, LoginFailureReason.BAD_CREDENTIALS);
        }

        assertEquals(900, retryAfter(() -> service.checkLogin(PERSONAL_ID, IP)));
        assertEquals(600, retryAfter(() -> service.checkLogin(PERSONAL_ID, OTHER_IP)));
    }

    @Test
    void anUnknownOrMissingPersonalId_isCountedLikeAnyOther() {
        failLogins(null, IP, 5);

        assertEquals(900, retryAfter(() -> service.checkLogin(null, OTHER_IP)));
        assertEquals(900, retryAfter(() -> service.checkLogin("", OTHER_IP)));
    }

    @Test
    void aFailedLogin_isOneWarningWithTheMaskedAccount_theAddress_andTheReason() {
        service.loginFailed("12.345.678-a", IP, LoginFailureReason.BAD_CREDENTIALS);
        service.loginFailed(PERSONAL_ID, IP, LoginFailureReason.DISABLED);

        List<ILoggingEvent> events = logs.warningsAndAbove();
        assertEquals(2, events.size());
        assertWarningWithoutStackTrace(events.get(0),
                "Failed login: account=***78A, ip=203.0.113.7, reason=BAD_CREDENTIALS");
        assertWarningWithoutStackTrace(events.get(1),
                "Failed login: account=***78A, ip=203.0.113.7, reason=DISABLED");
    }

    @Test
    void aWrongCurrentPassword_isOneWarningWithTheUserId_theAddress_andTheReason() {
        User user = UserMother.randomUserWithPersonalId(PERSONAL_ID);

        service.passwordChangeFailed(user, IP);

        List<ILoggingEvent> events = logs.warningsAndAbove();
        assertEquals(1, events.size());
        assertWarningWithoutStackTrace(events.get(0),
                "Failed password change: user=" + user.getId() + ", ip=203.0.113.7, reason=WRONG_CURRENT_PASSWORD");
    }

    @Test
    void aPersonalIdOfThreeCharactersOrFewer_isMaskedEntirely() {
        service.loginFailed("ab", IP, LoginFailureReason.BAD_CREDENTIALS);
        service.loginFailed("abc", IP, LoginFailureReason.BAD_CREDENTIALS);
        service.loginFailed(null, IP, LoginFailureReason.BAD_CREDENTIALS);

        logs.warningsAndAbove().forEach(event -> assertEquals(
                "Failed login: account=***, ip=203.0.113.7, reason=BAD_CREDENTIALS", event.getFormattedMessage()));
    }

    @Test
    void reachingTheAccountLimit_logsTheActivationOnce_withTheMaskedAccountAndTheWait() {
        failLogins(PERSONAL_ID, IP, 4);
        clock.advance(Duration.ofMinutes(1));
        logs.clear();

        service.loginFailed(PERSONAL_ID, IP, LoginFailureReason.BAD_CREDENTIALS);

        List<ILoggingEvent> events = logs.warningsAndAbove();
        assertEquals(2, events.size());
        assertWarningWithoutStackTrace(events.get(1),
                "Authentication throttled: scope=account, account=***78A, retryAfter=840s");
    }

    @Test
    void reachingTheAccountLimitThroughAPasswordChange_logsTheActivationWithTheUserId() {
        User user = UserMother.randomUserWithPersonalId(PERSONAL_ID);
        failLogins(PERSONAL_ID, IP, 4);
        logs.clear();

        service.passwordChangeFailed(user, IP);

        List<ILoggingEvent> events = logs.warningsAndAbove();
        assertEquals(2, events.size());
        assertWarningWithoutStackTrace(events.get(1),
                "Authentication throttled: scope=account, user=" + user.getId() + ", retryAfter=900s");
    }

    @Test
    void reachingTheAddressLimit_logsTheActivationOnce_withTheAddressAndTheWait() {
        for (int i = 0; i < 19; i++) {
            service.loginFailed("ACCOUNT" + i, IP, LoginFailureReason.BAD_CREDENTIALS);
        }
        logs.clear();

        service.loginFailed("LASTACCOUNT", IP, LoginFailureReason.BAD_CREDENTIALS);

        List<ILoggingEvent> events = logs.warningsAndAbove();
        assertEquals(2, events.size());
        assertWarningWithoutStackTrace(events.get(1), "Authentication throttled: scope=ip, ip=203.0.113.7, retryAfter=900s");
    }

    private void failLogins(String personalId, String clientIp, int times) {
        for (int i = 0; i < times; i++) {
            service.loginFailed(personalId, clientIp, LoginFailureReason.BAD_CREDENTIALS);
        }
    }

    private static long retryAfter(Executable check) {
        return assertThrows(TooManyFailedAttemptsException.class, check).getRetryAfterSeconds();
    }

    private static void assertWarningWithoutStackTrace(ILoggingEvent event, String message) {
        assertEquals(Level.WARN, event.getLevel());
        assertEquals(message, event.getFormattedMessage());
        assertNull(event.getThrowableProxy());
    }
}
