package org.lucoenergia.conluz.infrastructure.shared.security.auth.blacklist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.lucoenergia.conluz.domain.admin.user.auth.BlacklistedToken;

import java.time.Instant;

/**
 * JPA entity for storing blacklisted JWT tokens.
 */
@Entity(name = "blacklisted_token")
public class BlacklistedTokenEntity {

    @Id
    @Column(length = 64)
    private String jti;

    @Column(nullable = false)
    private Instant expiration;

    @Column(nullable = false)
    private Instant revokedAt;

    /**
     * Default constructor required by JPA.
     */
    protected BlacklistedTokenEntity() {
    }

    /**
     * Creates a new blacklisted token entity.
     *
     * @param jti        The JWT ID of the token
     * @param expiration The expiration date of the token
     * @param revokedAt  The time when the token was revoked
     */
    public BlacklistedTokenEntity(String jti, Instant expiration, Instant revokedAt) {
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
     * Sets the JWT ID of the token.
     *
     * @param jti The JWT ID
     */
    public void setJti(String jti) {
        this.jti = jti;
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
     * Sets the expiration date of the token.
     *
     * @param expiration The expiration date
     */
    public void setExpiration(Instant expiration) {
        this.expiration = expiration;
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
     * Sets the time when the token was revoked.
     *
     * @param revokedAt The revocation time
     */
    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    /**
     * Converts this entity to a domain model.
     *
     * @return The domain model
     */
    public BlacklistedToken toDomain() {
        return new BlacklistedToken(jti, expiration, revokedAt);
    }

    /**
     * Creates a new entity from a domain model.
     *
     * @param blacklistedToken The domain model
     * @return The entity
     */
    public static BlacklistedTokenEntity fromDomain(BlacklistedToken blacklistedToken) {
        return new BlacklistedTokenEntity(
                blacklistedToken.getJti(),
                blacklistedToken.getExpiration(),
                blacklistedToken.getRevokedAt()
        );
    }
}