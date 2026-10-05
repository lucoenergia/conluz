package org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.AuthenticationThrottleService;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.LoginAttempt;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.PasswordChangeAttempt;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.TooManyFailedAttemptsException;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;

/**
 * Holds the failed-attempt counters in memory, which is enough for the single instance the application runs as.
 * They are lost on restart.
 */
@Service
public class AuthenticationThrottleServiceImpl implements AuthenticationThrottleService {

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
    public LoginAttempt startLogin(String personalId, String clientIp) {
        String account = accountOf(personalId);
        String maskedAccount = mask(account);
        return new LoginAttemptImpl(reserve(account, "account=" + maskedAccount, clientIp), maskedAccount, clientIp);
    }

    @Override
    public PasswordChangeAttempt startPasswordChange(User user, String clientIp) {
        ReservedSlot slot = reserve(accountOf(user.getPersonalId()), "user=" + user.getId(), clientIp);
        return new PasswordChangeAttemptImpl(slot, user.getId(), clientIp);
    }

    /**
     * Reserves a slot on both counters, or none: when the client address refuses, the slot already reserved on the
     * account is released before the attempt is refused.
     */
    private ReservedSlot reserve(String account, String accountLabel, String clientIp) {
        Instant now = clock.instant();
        OptionalLong accountWait = accountCounter.tryReserve(account, now);
        if (accountWait.isPresent()) {
            throw new TooManyFailedAttemptsException(Math.max(accountWait.getAsLong(),
                    clientIpCounter.retryAfterSeconds(clientIp, now).orElse(0)));
        }
        OptionalLong clientIpWait = clientIpCounter.tryReserve(clientIp, now);
        if (clientIpWait.isPresent()) {
            accountCounter.release(account);
            throw new TooManyFailedAttemptsException(Math.max(clientIpWait.getAsLong(),
                    accountCounter.retryAfterSeconds(account, now).orElse(0)));
        }
        return new ReservedSlot(accountCounter, clientIpCounter, clock, account, accountLabel, clientIp);
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
