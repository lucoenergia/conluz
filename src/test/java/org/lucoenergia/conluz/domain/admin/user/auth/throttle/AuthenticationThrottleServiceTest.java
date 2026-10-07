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
import java.util.ArrayList;
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

        admitLogin(PERSONAL_ID, OTHER_IP);
    }

    @Test
    void fiveFailedLogins_throttleTheAccountFromAnyAddress_forTheWholeWindow() {
        failLogins(PERSONAL_ID, IP, 5);

        assertEquals(900, retryAfter(() -> service.startLogin(PERSONAL_ID, OTHER_IP)));
    }

    @Test
    void theWindowStartsAtTheFirstFailure_andTheAccountIsReleasedWhenItEnds() {
        failLogin(PERSONAL_ID, IP, LoginFailureReason.BAD_CREDENTIALS);
        clock.advance(Duration.ofMinutes(10));
        failLogins(PERSONAL_ID, IP, 4);

        assertEquals(300, retryAfter(() -> service.startLogin(PERSONAL_ID, IP)));

        clock.advance(Duration.ofMinutes(5).minusNanos(1));
        assertEquals(1, retryAfter(() -> service.startLogin(PERSONAL_ID, IP)));

        clock.advance(Duration.ofNanos(1));
        admitLogin(PERSONAL_ID, IP);
    }

    @Test
    void retryAfterIsTheRemainingTimeRoundedUpToWholeSeconds() {
        failLogins(PERSONAL_ID, IP, 5);

        clock.advance(Duration.ofMillis(1_500));

        assertEquals(899, retryAfter(() -> service.startLogin(PERSONAL_ID, IP)));
    }

    @Test
    void typingVariantsOfOnePersonalId_countAgainstTheSameAccount() {
        failLogin("12345678a", IP, LoginFailureReason.BAD_CREDENTIALS);
        failLogin(" 12345678 A ", IP, LoginFailureReason.BAD_CREDENTIALS);
        failLogin("12345678-A", IP, LoginFailureReason.BAD_CREDENTIALS);
        failLogin("12.345.678-A", IP, LoginFailureReason.BAD_CREDENTIALS);
        failLogin("12345678A", IP, LoginFailureReason.DISABLED);

        assertEquals(900, retryAfter(() -> service.startLogin("12.345.678-a", OTHER_IP)));
    }

    @Test
    void failedLoginsAndWrongCurrentPasswords_shareTheAccountCounter() {
        User user = UserMother.randomUserWithPersonalId(PERSONAL_ID);
        failLogins("12.345.678-a", IP, 3);
        failPasswordChange(user, IP);
        failPasswordChange(user, IP);

        assertEquals(900, retryAfter(() -> service.startLogin(PERSONAL_ID, OTHER_IP)));
        assertEquals(900, retryAfter(() -> service.startPasswordChange(user, OTHER_IP)));
    }

    @Test
    void twentyFailuresFromOneAddress_throttleThatAddressForEveryAccount_onBothEndpoints() {
        for (int i = 0; i < 19; i++) {
            failLogin("ACCOUNT" + i, IP, LoginFailureReason.BAD_CREDENTIALS);
        }
        admitLogin(PERSONAL_ID, IP);

        failPasswordChange(UserMother.randomUser(), IP);

        assertEquals(900, retryAfter(() -> service.startLogin(PERSONAL_ID, IP)));
        assertEquals(900, retryAfter(() -> service.startPasswordChange(UserMother.randomUser(), IP)));
        admitLogin(PERSONAL_ID, OTHER_IP);
    }

    @Test
    void aSuccessfulLogin_resetsTheAccountCounter_butNeverTheAddressCounter() {
        failLogins(PERSONAL_ID, IP, 4);

        succeedLogin("12.345.678-a", IP);

        failLogins(PERSONAL_ID, IP, 4);
        admitLogin(PERSONAL_ID, OTHER_IP);
        // 8 failures from the address so far; 12 more on other accounts reach its limit of 20
        for (int i = 0; i < 12; i++) {
            failLogin("ACCOUNT" + i, IP, LoginFailureReason.BAD_CREDENTIALS);
        }
        assertEquals(900, retryAfter(() -> service.startLogin("ANOTHERACCOUNT", IP)));
    }

    @Test
    void aSuccessfulPasswordChange_resetsTheAccountCounter() {
        User user = UserMother.randomUserWithPersonalId(PERSONAL_ID);
        failLogins(PERSONAL_ID, IP, 4);

        succeedPasswordChange(user, IP);

        failLogins(PERSONAL_ID, OTHER_IP, 4);
        admitLogin(PERSONAL_ID, "192.0.2.1");
    }

    @Test
    void whenBothCountersAreOverTheLimit_theLongerWaitIsGiven() {
        failLogins(PERSONAL_ID, OTHER_IP, 5);
        clock.advance(Duration.ofMinutes(5));
        for (int i = 0; i < 20; i++) {
            failLogin("ACCOUNT" + i, IP, LoginFailureReason.BAD_CREDENTIALS);
        }

        assertEquals(900, retryAfter(() -> service.startLogin(PERSONAL_ID, IP)));
        assertEquals(600, retryAfter(() -> service.startLogin(PERSONAL_ID, OTHER_IP)));
    }

    @Test
    void anUnknownOrMissingPersonalId_isCountedLikeAnyOther() {
        failLogins(null, IP, 5);

        assertEquals(900, retryAfter(() -> service.startLogin(null, OTHER_IP)));
        assertEquals(900, retryAfter(() -> service.startLogin("", OTHER_IP)));
    }

    @Test
    void aFailedLogin_isOneWarningWithTheMaskedAccount_theAddress_andTheReason() {
        failLogin("12.345.678-a", IP, LoginFailureReason.BAD_CREDENTIALS);
        failLogin(PERSONAL_ID, IP, LoginFailureReason.DISABLED);

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

        failPasswordChange(user, IP);

        List<ILoggingEvent> events = logs.warningsAndAbove();
        assertEquals(1, events.size());
        assertWarningWithoutStackTrace(events.get(0),
                "Failed password change: user=" + user.getId() + ", ip=203.0.113.7, reason=WRONG_CURRENT_PASSWORD");
    }

    @Test
    void aPersonalIdOfThreeCharactersOrFewer_isMaskedEntirely() {
        failLogin("ab", IP, LoginFailureReason.BAD_CREDENTIALS);
        failLogin("abc", IP, LoginFailureReason.BAD_CREDENTIALS);
        failLogin(null, IP, LoginFailureReason.BAD_CREDENTIALS);

        logs.warningsAndAbove().forEach(event -> assertEquals(
                "Failed login: account=***, ip=203.0.113.7, reason=BAD_CREDENTIALS", event.getFormattedMessage()));
    }

    @Test
    void reachingTheAccountLimit_logsTheActivationOnce_withTheMaskedAccountAndTheWait() {
        failLogins(PERSONAL_ID, IP, 4);
        clock.advance(Duration.ofMinutes(1));
        logs.clear();

        failLogin(PERSONAL_ID, IP, LoginFailureReason.BAD_CREDENTIALS);

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

        failPasswordChange(user, IP);

        List<ILoggingEvent> events = logs.warningsAndAbove();
        assertEquals(2, events.size());
        assertWarningWithoutStackTrace(events.get(1),
                "Authentication throttled: scope=account, user=" + user.getId() + ", retryAfter=900s");
    }

    @Test
    void reachingTheAddressLimit_logsTheActivationOnce_withTheAddressAndTheWait() {
        for (int i = 0; i < 19; i++) {
            failLogin("ACCOUNT" + i, IP, LoginFailureReason.BAD_CREDENTIALS);
        }
        logs.clear();

        failLogin("LASTACCOUNT", IP, LoginFailureReason.BAD_CREDENTIALS);

        List<ILoggingEvent> events = logs.warningsAndAbove();
        assertEquals(2, events.size());
        assertWarningWithoutStackTrace(events.get(1), "Authentication throttled: scope=ip, ip=203.0.113.7, retryAfter=900s");
    }

    @Test
    void fiveLoginsInProgress_refuseASixth_beforeAnyOfThemHasFailed() {
        List<LoginAttempt> inProgress = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            inProgress.add(service.startLogin(PERSONAL_ID, "192.0.2." + i));
        }

        assertEquals(900, retryAfter(() -> service.startLogin(PERSONAL_ID, OTHER_IP)));

        inProgress.forEach(attempt -> attempt.failed(LoginFailureReason.BAD_CREDENTIALS));
        assertEquals(900, retryAfter(() -> service.startLogin(PERSONAL_ID, OTHER_IP)));
    }

    @Test
    void twentyAttemptsInProgressFromOneAddress_refuseAnother_onBothEndpoints() {
        for (int i = 0; i < 20; i++) {
            service.startLogin("ACCOUNT" + i, IP);
        }

        assertEquals(900, retryAfter(() -> service.startLogin(PERSONAL_ID, IP)));
        assertEquals(900, retryAfter(() -> service.startPasswordChange(UserMother.randomUser(), IP)));
    }

    @Test
    void anAttemptClosedWithoutAnOutcome_releasesItsSlot_andCountsNoFailure() {
        for (int i = 0; i < 5; i++) {
            service.startLogin(PERSONAL_ID, IP).close();
        }
        for (int i = 0; i < 5; i++) {
            service.startPasswordChange(UserMother.randomUserWithPersonalId(PERSONAL_ID), IP).close();
        }

        failLogins(PERSONAL_ID, IP, 4);
        admitLogin(PERSONAL_ID, IP);
    }

    @Test
    void closingASettledAttempt_doesNotReleaseItsSlotTwice() {
        for (int i = 0; i < 3; i++) {
            service.startLogin(PERSONAL_ID, IP);
        }
        LoginAttempt settled = service.startLogin(PERSONAL_ID, IP);
        settled.failed(LoginFailureReason.BAD_CREDENTIALS);
        settled.close();

        // One failure and three slots held: one slot is left. A second release would have freed one more
        service.startLogin(PERSONAL_ID, OTHER_IP);
        assertEquals(900, retryAfter(() -> service.startLogin(PERSONAL_ID, OTHER_IP)));
    }

    @Test
    void anAttemptRefusedByTheAddress_releasesTheSlotItReservedOnTheAccount() {
        for (int i = 0; i < 20; i++) {
            failLogin("ACCOUNT" + i, IP, LoginFailureReason.BAD_CREDENTIALS);
        }
        for (int i = 0; i < 10; i++) {
            assertThrows(TooManyFailedAttemptsException.class, () -> service.startLogin(PERSONAL_ID, IP));
        }

        for (int i = 0; i < 5; i++) {
            service.startLogin(PERSONAL_ID, OTHER_IP);
        }
    }

    @Test
    void everyPasswordResetRequest_countsAgainstTheClientAddress_andTheTwentyFirstIsRefused() {
        for (int i = 0; i < 20; i++) {
            service.countPasswordResetRequest(IP);
        }

        assertEquals(900, retryAfter(() -> service.countPasswordResetRequest(IP)));
        assertDoesNotThrow(() -> service.countPasswordResetRequest(OTHER_IP));
    }

    @Test
    void passwordResetRequests_logOnlyWhenTheClientAddressReachesItsLimit() {
        for (int i = 0; i < 20; i++) {
            service.countPasswordResetRequest(IP);
        }

        List<ILoggingEvent> warnings = logs.warningsAndAbove();
        assertEquals(1, warnings.size());
        assertWarningWithoutStackTrace(warnings.get(0),
                "Authentication throttled: scope=ip, ip=203.0.113.7, retryAfter=900s");
    }

    @Test
    void passwordResetRequests_andInvalidResetTokens_addUpWithFailedLogins_onTheClientAddressOnly() {
        // 18 different accounts, so no account reaches its own limit
        for (int i = 0; i < 18; i++) {
            failLogin("ACCOUNT" + i, IP, LoginFailureReason.BAD_CREDENTIALS);
        }
        service.countPasswordResetRequest(IP);
        failPasswordReset(IP);

        assertEquals(900, retryAfter(() -> service.startLogin(PERSONAL_ID, IP)));
        assertEquals(900, retryAfter(() -> service.startPasswordReset(IP)));
        // No account counted the recovery
        admitLogin(PERSONAL_ID, OTHER_IP);
    }

    @Test
    void passwordResetRequests_neverCountAgainstAnyAccount() {
        for (int i = 0; i < 5; i++) {
            service.countPasswordResetRequest(IP);
        }

        admitLogin(PERSONAL_ID, OTHER_IP);
        admitLogin("", OTHER_IP);
    }

    @Test
    void anInvalidResetToken_isLoggedOnce_withTheClientAddressOnly() {
        failPasswordReset(IP);

        List<ILoggingEvent> warnings = logs.warningsAndAbove();
        assertEquals(1, warnings.size());
        assertWarningWithoutStackTrace(warnings.get(0), "Failed password reset: ip=203.0.113.7, reason=INVALID_TOKEN");
    }

    @Test
    void aResetSettledByNeither_countsNothing() {
        for (int i = 0; i < 25; i++) {
            service.startPasswordReset(IP).close();
        }

        assertDoesNotThrow(() -> service.startPasswordReset(IP).close());
        assertEquals(0, logs.warningsAndAbove().size());
    }

    @Test
    void aSuccessfulReset_resetsTheAccountOfItsUser_butNotTheClientAddress() {
        User user = UserMother.randomUserWithPersonalId(PERSONAL_ID);
        failLogins(PERSONAL_ID, OTHER_IP, 5);
        for (int i = 0; i < 19; i++) {
            failPasswordReset(IP);
        }
        assertEquals(900, retryAfter(() -> service.startLogin(PERSONAL_ID, OTHER_IP)));

        try (PasswordResetAttempt attempt = service.startPasswordReset(IP)) {
            attempt.succeeded(user);
        }

        admitLogin(" 12345678 a ", OTHER_IP);
        // The client address keeps its 19 failures: one more and it is throttled
        failPasswordReset(IP);
        assertEquals(900, retryAfter(() -> service.startPasswordReset(IP)));
    }

    @Test
    void concurrentResets_fromOneClientAddress_stayWithinItsLimit() {
        List<PasswordResetAttempt> inProgress = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            inProgress.add(service.startPasswordReset(IP));
        }

        assertThrows(TooManyFailedAttemptsException.class, () -> service.startPasswordReset(IP));
        inProgress.forEach(PasswordResetAttempt::close);
        assertDoesNotThrow(() -> service.startPasswordReset(IP).close());
    }

    private void failPasswordReset(String clientIp) {
        try (PasswordResetAttempt attempt = service.startPasswordReset(clientIp)) {
            attempt.failed();
        }
    }

    private void admitLogin(String personalId, String clientIp) {
        assertDoesNotThrow(() -> service.startLogin(personalId, clientIp).close());
    }

    private void failLogin(String personalId, String clientIp, LoginFailureReason reason) {
        try (LoginAttempt attempt = service.startLogin(personalId, clientIp)) {
            attempt.failed(reason);
        }
    }

    private void succeedLogin(String personalId, String clientIp) {
        try (LoginAttempt attempt = service.startLogin(personalId, clientIp)) {
            attempt.succeeded();
        }
    }

    private void failPasswordChange(User user, String clientIp) {
        try (PasswordChangeAttempt attempt = service.startPasswordChange(user, clientIp)) {
            attempt.failed();
        }
    }

    private void succeedPasswordChange(User user, String clientIp) {
        try (PasswordChangeAttempt attempt = service.startPasswordChange(user, clientIp)) {
            attempt.succeeded();
        }
    }

    private void failLogins(String personalId, String clientIp, int times) {
        for (int i = 0; i < times; i++) {
            failLogin(personalId, clientIp, LoginFailureReason.BAD_CREDENTIALS);
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
