package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.PlatformAdminInvitation;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.time.Instant;
import java.util.UUID;

/**
 * A stored platform-admin invitation. It holds the hash of the confirmation code, never the code itself.
 */
@Entity(name = "platform_admin_invitation")
public class PlatformAdminInvitationEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 250)
    private String email;

    @Column(nullable = false, length = 250)
    private String fullName;

    private UUID invitedBy;

    @Column(nullable = false)
    private Instant invitedAt;

    private Instant acceptedAt;

    @Column(nullable = false, length = 64)
    private String confirmationCodeHash;

    @Column(nullable = false)
    private int failedCodeAttempts;

    /**
     * Default constructor required by JPA.
     */
    protected PlatformAdminInvitationEntity() {
    }

    public PlatformAdminInvitationEntity(UUID id, UUID userId, String email, String fullName, UUID invitedBy,
                                         Instant invitedAt, String confirmationCodeHash) {
        this.id = id;
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.invitedBy = invitedBy;
        this.invitedAt = invitedAt;
        this.confirmationCodeHash = confirmationCodeHash;
        this.failedCodeAttempts = 0;
    }

    public PlatformAdminInvitation toDomain() {
        return new PlatformAdminInvitation(id, UserId.of(userId), email, fullName, invitedBy, invitedAt, acceptedAt,
                confirmationCodeHash, failedCodeAttempts);
    }

    void replaceConfirmationCode(String confirmationCodeHash) {
        this.confirmationCodeHash = confirmationCodeHash;
        this.failedCodeAttempts = 0;
    }

    int recordFailedCodeAttempt() {
        return ++failedCodeAttempts;
    }

    void markAccepted(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }
}
