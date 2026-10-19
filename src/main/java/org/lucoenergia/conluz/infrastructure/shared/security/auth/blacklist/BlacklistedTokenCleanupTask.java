package org.lucoenergia.conluz.infrastructure.shared.security.auth.blacklist;

import org.lucoenergia.conluz.domain.admin.user.auth.BlacklistedTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Scheduled task to clean up expired blacklisted tokens.
 */
@Component
public class BlacklistedTokenCleanupTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlacklistedTokenCleanupTask.class);

    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final Clock clock;

    public BlacklistedTokenCleanupTask(BlacklistedTokenRepository blacklistedTokenRepository, Clock clock) {
        this.blacklistedTokenRepository = blacklistedTokenRepository;
        this.clock = clock;
    }

    /**
     * Cleans up expired blacklisted tokens every hour.
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    public void cleanupExpiredTokens() {
        LOGGER.info("Starting cleanup of expired blacklisted tokens");
        int deletedCount = blacklistedTokenRepository.deleteAllExpiredBefore(clock.instant());
        LOGGER.info("Deleted {} expired blacklisted tokens", deletedCount);
    }
}