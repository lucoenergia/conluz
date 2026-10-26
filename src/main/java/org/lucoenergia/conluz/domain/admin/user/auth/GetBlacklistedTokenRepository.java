package org.lucoenergia.conluz.domain.admin.user.auth;

/**
 * Reads the blacklist of JWT tokens.
 */
public interface GetBlacklistedTokenRepository {

    /**
     * Checks if a token with the given JWT ID exists in the blacklist.
     *
     * @param jti The JWT ID to check
     * @return true if the token is blacklisted, false otherwise
     */
    boolean existsByJti(String jti);
}
