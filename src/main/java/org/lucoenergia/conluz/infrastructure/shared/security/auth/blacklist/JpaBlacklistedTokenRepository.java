package org.lucoenergia.conluz.infrastructure.shared.security.auth.blacklist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/**
 * JPA repository for blacklisted tokens.
 */
public interface JpaBlacklistedTokenRepository extends JpaRepository<BlacklistedTokenEntity, String> {

    /**
     * Checks if a token with the given JWT ID exists in the blacklist.
     *
     * @param jti The JWT ID to check
     * @return true if the token is blacklisted, false otherwise
     */
    boolean existsByJti(String jti);

    /**
     * Deletes all blacklisted tokens that have expired before the given time.
     *
     * @param before The time before which tokens are considered expired
     * @return The number of tokens deleted
     */
    @Modifying
    @Query("DELETE FROM blacklisted_token b WHERE b.expiration < :before")
    int deleteAllByExpirationBefore(@Param("before") Instant before);
}