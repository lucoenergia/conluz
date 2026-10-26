package org.lucoenergia.conluz.domain.admin.user.auth;

/**
 * Adds JWT tokens to the blacklist.
 */
public interface CreateBlacklistedTokenRepository {

    /**
     * Saves a blacklisted token to the repository.
     *
     * @param blacklistedToken The token to blacklist
     * @return The saved blacklisted token
     */
    BlacklistedToken save(BlacklistedToken blacklistedToken);
}
