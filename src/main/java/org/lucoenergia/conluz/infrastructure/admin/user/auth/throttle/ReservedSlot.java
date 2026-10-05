package org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;

/**
 * One admitted attempt's reservation on the account and client address counters, settled exactly once: as a
 * failure, as a success, or released. Settling it again has no effect, so closing an attempt after its outcome has
 * been recorded never frees a slot that another attempt holds.
 */
final class ReservedSlot {

    // The throttling logs under one category, whichever class writes the line
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationThrottleServiceImpl.class);

    private final FailedAttemptCounter accountCounter;
    private final FailedAttemptCounter clientIpCounter;
    private final Clock clock;
    private final String account;
    private final String accountLabel;
    private final String clientIp;
    private boolean settled;

    /**
     * @param accountLabel how the account is named in the log: the masked personal ID, or the user ID
     */
    ReservedSlot(FailedAttemptCounter accountCounter, FailedAttemptCounter clientIpCounter, Clock clock,
                 String account, String accountLabel, String clientIp) {
        this.accountCounter = accountCounter;
        this.clientIpCounter = clientIpCounter;
        this.clock = clock;
        this.account = account;
        this.accountLabel = accountLabel;
        this.clientIp = clientIp;
    }

    /**
     * Counts the attempt as a failure on both counters, and logs each counter this failure makes reach its limit.
     */
    void failed() {
        if (settle()) {
            Instant now = clock.instant();
            if (accountCounter.confirmFailure(account, now)) {
                LOGGER.warn("Authentication throttled: scope=account, {}, retryAfter={}s", accountLabel,
                        accountCounter.retryAfterSeconds(account, now).orElse(0));
            }
            if (clientIpCounter.confirmFailure(clientIp, now)) {
                LOGGER.warn("Authentication throttled: scope=ip, ip={}, retryAfter={}s", clientIp,
                        clientIpCounter.retryAfterSeconds(clientIp, now).orElse(0));
            }
        }
    }

    /**
     * Resets the account's failures and frees the slot. The client address's failures are never reset.
     */
    void succeeded() {
        if (settle()) {
            accountCounter.reset(account);
            accountCounter.release(account);
            clientIpCounter.release(clientIp);
        }
    }

    /**
     * Frees the slot without counting anything, unless the attempt was already settled.
     */
    void release() {
        if (settle()) {
            accountCounter.release(account);
            clientIpCounter.release(clientIp);
        }
    }

    private boolean settle() {
        if (settled) {
            return false;
        }
        settled = true;
        return true;
    }
}
