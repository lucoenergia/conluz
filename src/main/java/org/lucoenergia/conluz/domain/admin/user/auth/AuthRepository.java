package org.lucoenergia.conluz.domain.admin.user.auth;

import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

public interface AuthRepository {

    Token getToken(User user);

    /**
     * Verifies the token's signature, format and expiry, and that it carries the claims every issued token has.
     *
     * @param token The token
     * @return The token's verified subject and id
     * @throws RuntimeException carrying the {@link TokenRejectionReason} when the token is rejected
     */
    VerifiedToken verify(Token token);

    /**
     * Checks a verified token against the user it was issued for.
     *
     * @param token The token
     * @param user  The user named by the token's subject
     * @return The rule that rejects the token, or empty if the token is valid for that user
     */
    Optional<TokenRejectionReason> findRejectionReason(Token token, User user);

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
