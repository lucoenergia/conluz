package org.lucoenergia.conluz.infrastructure.admin.user.token;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;

import java.time.Instant;
import java.util.UUID;

/**
 * A stored one-time token. It holds the hash of the token, never the token itself.
 */
@Entity(name = "one_time_token")
public class OneTimeTokenEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OneTimeTokenPurpose purpose;

    @Column(nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;

    private Instant revokedAt;

    /**
     * The membership the token was issued for, or null when the token is bound only to its user.
     */
    private UUID membershipId;

    /**
     * The platform-admin invitation the token was issued for, or null when the token is not bound to one.
     */
    private UUID platformAdminInvitationId;

    /**
     * Default constructor required by JPA.
     */
    protected OneTimeTokenEntity() {
    }

    public OneTimeTokenEntity(UUID id, UUID userId, OneTimeTokenPurpose purpose, String tokenHash, Instant createdAt,
                              Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public OneTimeTokenEntity(UUID id, UUID userId, UUID membershipId, OneTimeTokenPurpose purpose, String tokenHash,
                              Instant createdAt, Instant expiresAt) {
        this(id, userId, purpose, tokenHash, createdAt, expiresAt);
        this.membershipId = membershipId;
    }

    public static OneTimeTokenEntity forPlatformAdminInvitation(UUID id, UUID userId, UUID invitationId,
                                                                OneTimeTokenPurpose purpose, String tokenHash,
                                                                Instant createdAt, Instant expiresAt) {
        OneTimeTokenEntity entity = new OneTimeTokenEntity(id, userId, purpose, tokenHash, createdAt, expiresAt);
        entity.platformAdminInvitationId = invitationId;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getMembershipId() {
        return membershipId;
    }

    public UUID getPlatformAdminInvitationId() {
        return platformAdminInvitationId;
    }

    public OneTimeTokenPurpose getPurpose() {
        return purpose;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
