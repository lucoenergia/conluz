package org.lucoenergia.conluz.domain.admin.user.auth;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a JWT token that has been blacklisted (revoked).
 * Blacklisted tokens are considered invalid even if they haven't expired yet.
 */
public class BlacklistedToken {

    private final String jti;
    private final Instant expiration;
    private final Instant revokedAt;

    /**
     * Creates a new blacklisted token.
     *
     * @param jti        The JWT ID of the token
     * @param expiration The expiration date of the token
     */
    public BlacklistedToken(String jti, Instant expiration) {
        this(jti, expiration, Instant.now());
    }

    /**
     * Creates a new blacklisted token with a specific revocation time.
     *
     * @param jti        The JWT ID of the token
     * @param expiration The expiration date of the token
     * @param revokedAt  The time when the token was revoked
     */
    public BlacklistedToken(String jti, Instant expiration, Instant revokedAt) {
        this.jti = jti;
        this.expiration = expiration;
        this.revokedAt = revokedAt;
    }

    /**
     * Gets the JWT ID of the token.
     *
     * @return The JWT ID
     */
    public String getJti() {
        return jti;
    }

    /**
     * Gets the expiration date of the token.
     *
     * @return The expiration date
     */
    public Instant getExpiration() {
        return expiration;
    }

    /**
     * Gets the time when the token was revoked.
     *
     * @return The revocation time
     */
    public Instant getRevokedAt() {
        return revokedAt;
    }

    /**
     * Checks if the token has expired.
     *
     * @return true if the token has expired, false otherwise
     */
    public boolean isExpired() {
        return expiration.isBefore(Instant.now());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlacklistedToken that = (BlacklistedToken) o;
        return Objects.equals(jti, that.jti);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jti);
    }
}