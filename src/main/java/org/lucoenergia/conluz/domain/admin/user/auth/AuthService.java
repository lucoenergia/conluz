package org.lucoenergia.conluz.domain.admin.user.auth;

import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.Optional;

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

    /**
     * Retrieves the currently authenticated user from the context or session.
     *
     * @return an Optional containing the currently authenticated User if present; otherwise, an empty Optional
     */
    Optional<User> getCurrentUser();
}
