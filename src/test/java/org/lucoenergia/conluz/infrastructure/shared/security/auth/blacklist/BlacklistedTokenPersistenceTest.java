package org.lucoenergia.conluz.infrastructure.shared.security.auth.blacklist;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.auth.BlacklistedToken;
import org.lucoenergia.conluz.domain.admin.user.auth.CreateBlacklistedTokenRepository;
import org.lucoenergia.conluz.domain.admin.user.auth.DeleteBlacklistedTokenRepository;
import org.lucoenergia.conluz.domain.admin.user.auth.GetBlacklistedTokenRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The blacklist's instants as the application stores and compares them, now that the columns are TIMESTAMPTZ (#481).
 */
@Transactional
class BlacklistedTokenPersistenceTest extends BaseIntegrationTest {

    private static final Instant EXPIRATION = Instant.parse("2026-07-01T10:00:00Z");
    private static final Instant REVOKED_AT = Instant.parse("2026-01-15T11:00:00Z");

    @Autowired
    private CreateBlacklistedTokenRepository createBlacklistedTokenRepository;
    @Autowired
    private GetBlacklistedTokenRepository getBlacklistedTokenRepository;
    @Autowired
    private DeleteBlacklistedTokenRepository deleteBlacklistedTokenRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void theStoredValuesAreTheInstantsSaved() {
        createBlacklistedTokenRepository.save(new BlacklistedToken("stored-instants", EXPIRATION, REVOKED_AT));
        entityManager.flush();

        assertEquals(EXPIRATION, column("expiration", "stored-instants"));
        assertEquals(REVOKED_AT, column("revoked_at", "stored-instants"));
    }

    @Test
    void onlyTokensThatExpiredBeforeTheGivenInstantAreDeleted() {
        createBlacklistedTokenRepository.save(new BlacklistedToken("expiring", EXPIRATION, REVOKED_AT));
        entityManager.flush();

        assertEquals(0, deleteBlacklistedTokenRepository.deleteAllExpiredBefore(EXPIRATION));
        assertTrue(getBlacklistedTokenRepository.existsByJti("expiring"));

        assertEquals(1, deleteBlacklistedTokenRepository.deleteAllExpiredBefore(EXPIRATION.plus(Duration.ofSeconds(1))));
        assertFalse(getBlacklistedTokenRepository.existsByJti("expiring"));
    }

    private Instant column(String column, String jti) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM blacklisted_token WHERE jti = ?",
                OffsetDateTime.class, jti).toInstant();
    }
}
