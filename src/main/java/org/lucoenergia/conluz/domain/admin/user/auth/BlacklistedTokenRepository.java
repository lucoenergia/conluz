package org.lucoenergia.conluz.domain.admin.user.auth;

import java.time.Instant;

/**
 * Repository for managing blacklisted JWT tokens.
 */
public interface BlacklistedTokenRepository {

    /**
     * Saves a blacklisted token to the repository.
     *
     * @param blacklistedToken The token to blacklist
     * @return The saved blacklisted token
     */
    BlacklistedToken save(BlacklistedToken blacklistedToken);

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
    int deleteAllExpiredBefore(Instant before);
}