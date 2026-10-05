package org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.AuthenticationThrottleService;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.LoginFailureReason;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.TooManyFailedAttemptsException;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Holds the failed-attempt counters in memory, which is enough for the single instance the application runs as.
 * They are lost on restart.
 */
@Service
public class AuthenticationThrottleServiceImpl implements AuthenticationThrottleService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationThrottleServiceImpl.class);

    static final int ACCOUNT_LIMIT = 5;
    static final int CLIENT_IP_LIMIT = 20;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Clock clock;
    private final FailedAttemptCounter accountCounter = new FailedAttemptCounter(ACCOUNT_LIMIT, WINDOW);
    private final FailedAttemptCounter clientIpCounter = new FailedAttemptCounter(CLIENT_IP_LIMIT, WINDOW);

    public AuthenticationThrottleServiceImpl(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void checkLogin(String personalId, String clientIp) {
        check(accountOf(personalId), clientIp);
    }

    @Override
    public void loginFailed(String personalId, String clientIp, LoginFailureReason reason) {
        String account = accountOf(personalId);
        LOGGER.warn("Failed login: account={}, ip={}, reason={}", mask(account), clientIp, reason);
        recordFailure(account, "account=" + mask(account), clientIp);
    }

    @Override
    public void loginSucceeded(String personalId) {
        accountCounter.reset(accountOf(personalId));
    }

    @Override
    public void checkPasswordChange(User user, String clientIp) {
        check(accountOf(user.getPersonalId()), clientIp);
    }

    @Override
    public void passwordChangeFailed(User user, String clientIp) {
        LOGGER.warn("Failed password change: user={}, ip={}, reason=WRONG_CURRENT_PASSWORD", user.getId(), clientIp);
        recordFailure(accountOf(user.getPersonalId()), "user=" + user.getId(), clientIp);
    }

    @Override
    public void passwordChangeSucceeded(User user) {
        accountCounter.reset(accountOf(user.getPersonalId()));
    }

    private void check(String account, String clientIp) {
        Instant now = clock.instant();
        long retryAfterSeconds = Math.max(
                accountCounter.retryAfterSeconds(account, now).orElse(0),
                clientIpCounter.retryAfterSeconds(clientIp, now).orElse(0));
        if (retryAfterSeconds > 0) {
            throw new TooManyFailedAttemptsException(retryAfterSeconds);
        }
    }

    private void recordFailure(String account, String accountLabel, String clientIp) {
        Instant now = clock.instant();
        if (accountCounter.recordFailure(account, now)) {
            LOGGER.warn("Authentication throttled: scope=account, {}, retryAfter={}s", accountLabel,
                    accountCounter.retryAfterSeconds(account, now).orElse(0));
        }
        if (clientIpCounter.recordFailure(clientIp, now)) {
            LOGGER.warn("Authentication throttled: scope=ip, ip={}, retryAfter={}s", clientIp,
                    clientIpCounter.retryAfterSeconds(clientIp, now).orElse(0));
        }
    }

    /**
     * The same normalisation the user lookup applies, so typing variants of one personal ID are one account, and
     * an unknown personal ID is counted exactly like an existing one.
     */
    private static String accountOf(String personalId) {
        String normalized = UserPersonalId.normalize(personalId);
        return normalized == null ? "" : normalized;
    }

    /**
     * {@code ***} followed by the last three characters, or {@code ***} alone when there are no more than three, so
     * that the full personal ID is never logged. Anything but an ASCII letter or digit is replaced, because the
     * value comes from the client.
     */
    static String mask(String account) {
        if (account.length() <= 3) {
            return "***";
        }
        return "***" + account.substring(account.length() - 3).replaceAll("[^A-Za-z0-9]", "?");
    }
}
