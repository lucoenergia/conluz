package org.lucoenergia.conluz.infrastructure.shared.security.auth.blacklist;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.auth.BlacklistedToken;
import org.lucoenergia.conluz.domain.admin.user.auth.BlacklistedTokenRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.time.MutableClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class BlacklistedTokenCleanupTaskTest extends BaseIntegrationTest {

    @Autowired
    private BlacklistedTokenCleanupTask cleanupTask;
    @Autowired
    private BlacklistedTokenRepository blacklistedTokenRepository;
    @Autowired
    private MutableClock clock;

    @Test
    void theCleanupRemovesTheEntriesOfExpiredTokens_andKeepsTheOthers_asTheClockMoves() {
        Instant now = clock.instant();
        String expiresFirst = blacklist(now.plus(Duration.ofHours(1)));
        String expiresSecond = blacklist(now.plus(Duration.ofHours(2)));
        String expiresLast = blacklist(now.plus(Duration.ofHours(4)));

        cleanupTask.cleanupExpiredTokens();
        assertTrue(blacklistedTokenRepository.existsByJti(expiresFirst));
        assertTrue(blacklistedTokenRepository.existsByJti(expiresSecond));
        assertTrue(blacklistedTokenRepository.existsByJti(expiresLast));

        clock.advance(Duration.ofHours(3));
        cleanupTask.cleanupExpiredTokens();
        assertFalse(blacklistedTokenRepository.existsByJti(expiresFirst));
        assertFalse(blacklistedTokenRepository.existsByJti(expiresSecond));
        assertTrue(blacklistedTokenRepository.existsByJti(expiresLast));

        clock.advance(Duration.ofHours(2));
        cleanupTask.cleanupExpiredTokens();
        assertFalse(blacklistedTokenRepository.existsByJti(expiresLast));
    }

    private String blacklist(Instant expiration) {
        String jti = UUID.randomUUID().toString();
        blacklistedTokenRepository.save(new BlacklistedToken(jti, expiration, clock.instant()));
        return jti;
    }
}
