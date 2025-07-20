package org.lucoenergia.conluz.domain.admin.user.auth;

import org.lucoenergia.conluz.domain.admin.user.User;

public interface AuthService {

    /**
     * Authenticates a user using their credentials and returns a token.
     *
     * @param credentials The user's credentials containing username and password
     * @return A token representing the authenticated session
     */
    Token login(Credentials credentials);

    /**
     * Logs out the currently authenticated user and invalidates their session.
     *
     * This method is responsible for clearing the current user's authentication context,
     * ensuring that they are no longer considered logged in.
     */
    void logout();

    /**
     * Blacklists a token to prevent it from being used again.
     *
     * @param token The token to blacklist
     * @return true if the token was successfully blacklisted, false otherwise
     */
    boolean blacklistToken(Token token);

    /**
     * Retrieves the currently authenticated user.
     *
     * @return the current authenticated user, or null if no user is authenticated
     */
    User getCurrentUser();
}
