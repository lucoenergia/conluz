package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import org.lucoenergia.conluz.domain.admin.user.auth.TokenRejectionReason;

import java.util.Optional;
import java.util.UUID;

/**
 * A token presented on a request was rejected. It never holds the token, nor the library exception that rejected
 * it, whose message may describe the token: a bearer token is a credential, and an exception can end up in a log.
 */
public class InvalidTokenException extends RuntimeException {

    private final TokenRejectionReason reason;
    private final UUID userId;

    /**
     * For a token whose claims could not be trusted, so its subject is not known.
     */
    public InvalidTokenException(TokenRejectionReason reason) {
        this(reason, null);
    }

    /**
     * @param userId the token's subject, only when its signature has been verified
     */
    public InvalidTokenException(TokenRejectionReason reason, UUID userId) {
        super(reason.name());
        this.reason = reason;
        this.userId = userId;
    }

    public TokenRejectionReason getReason() {
        return reason;
    }

    public Optional<UUID> getUserId() {
        return Optional.ofNullable(userId);
    }
}
