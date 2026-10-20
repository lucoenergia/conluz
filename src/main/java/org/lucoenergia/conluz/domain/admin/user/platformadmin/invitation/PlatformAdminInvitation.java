package org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.time.Instant;
import java.util.UUID;

/**
 * An invitation to the platform-admin role (ADR-0004 §2). Pending until it is accepted with its link and its
 * confirmation code. Only the hash of the code is held, and it is left out of {@link #toString()}.
 *
 * @param invitedBy the platform admin who invited, or null if that account has been deleted
 * @param acceptedAt when the invitation was accepted, or null while it is pending
 */
public record PlatformAdminInvitation(UUID id, UserId userId, String email, String fullName, UUID invitedBy,
                                      Instant invitedAt, Instant acceptedAt, String confirmationCodeHash,
                                      int failedCodeAttempts) {

    public boolean isPending() {
        return acceptedAt == null;
    }

    @Override
    public String toString() {
        return "PlatformAdminInvitation[id=" + id + ", userId=" + userId.getId() + ", acceptedAt=" + acceptedAt
                + ", failedCodeAttempts=" + failedCodeAttempts + "]";
    }
}
