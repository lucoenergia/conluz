package org.lucoenergia.conluz.domain.admin.user.auth;

import java.time.Instant;

/**
 * Removes JWT tokens from the blacklist.
 */
public interface DeleteBlacklistedTokenRepository {

    /**
     * Deletes all blacklisted tokens that have expired before the given time.
     *
     * @param before The time before which tokens are considered expired
     * @return The number of tokens deleted
     */
    int deleteAllExpiredBefore(Instant before);
}
