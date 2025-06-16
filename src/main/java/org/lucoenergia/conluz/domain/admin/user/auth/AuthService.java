package org.lucoenergia.conluz.domain.admin.user.auth;

public interface AuthService {

    Token login(Credentials credentials);

    void logout();

    /**
     * Blacklists a token to prevent it from being used again.
     *
     * @param token The token to blacklist
     * @return true if the token was successfully blacklisted, false otherwise
     */
    boolean blacklistToken(Token token);
}
