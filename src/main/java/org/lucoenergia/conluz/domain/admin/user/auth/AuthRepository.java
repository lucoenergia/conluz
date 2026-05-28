package org.lucoenergia.conluz.domain.admin.user.auth;

import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.Date;
import java.util.Map;
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

    /**
     * Returns whether the token owner is a platform admin.
     *
     * @param token The token
     * @return true if the token was issued for a platform admin
     */
    boolean isPlatformAdmin(Token token);

    /**
     * Returns the community memberships encoded in the token.
     *
     * @param token The token
     * @return A map of community id (string) to role name (string)
     */
    Map<String, String> getCommunityMemberships(Token token);
}
