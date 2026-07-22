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
     * Intended for {@code CommunityAccessGuard} implementations, which run as part of
     * {@code @PreAuthorize} evaluation itself. Controllers must not call this method — by the time
     * a {@code @PreAuthorize}-guarded controller method runs, the authenticated user is already
     * guaranteed and should be obtained via {@code @AuthenticationPrincipal} instead.
     *
     * @return an Optional containing the currently authenticated User if present; otherwise, an empty Optional
     */
    Optional<User> getCurrentUser();
}
