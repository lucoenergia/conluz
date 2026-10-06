package org.lucoenergia.conluz.infrastructure.admin.user.token;

import org.lucoenergia.conluz.domain.admin.user.token.DeleteOneTimeTokenRepository;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenRetention;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Deletes the one-time tokens that expired, were used or were revoked more than
 * {@link OneTimeTokenRetention#PERIOD} ago.
 */
@Component
public class OneTimeTokenCleanupTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(OneTimeTokenCleanupTask.class);

    private final DeleteOneTimeTokenRepository repository;
    private final Clock clock;

    public OneTimeTokenCleanupTask(DeleteOneTimeTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Runs every hour, at half past, away from the blacklisted token cleanup on the hour.
     */
    @Scheduled(cron = "0 30 * * * *")
    public void cleanup() {
        int deleted = repository.deleteFinishedBefore(clock.instant().minus(OneTimeTokenRetention.PERIOD));
        LOGGER.info("Deleted {} finished one-time tokens", deleted);
    }
}
