package org.lucoenergia.conluz.domain.admin.user.auth;

import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

public interface AuthRepository {

    Token getToken(User user);

    boolean isTokenValid(Token token, User user);

    UUID getUserIdFromToken(Token token);

    String getRole(Token token);

    Date getExpirationDate(Token token);

    /**
     * Extracts the JWT ID (jti) from the token.
     *
     * @param token The token
     * @return The JWT ID, or empty if not present or if an error occurs
     */
    Optional<String> getJtiFromToken(Token token);
}
