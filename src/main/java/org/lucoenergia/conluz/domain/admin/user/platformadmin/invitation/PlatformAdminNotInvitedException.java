package org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

/**
 * The user has no pending invitation to the platform-admin role, so there is nothing to resend or revoke.
 */
public class PlatformAdminNotInvitedException extends RuntimeException {

    private final UUID userId;

    public PlatformAdminNotInvitedException(UserId userId) {
        super("User with ID '" + userId.getId() + "' has no pending platform-admin invitation");
        this.userId = userId.getId();
    }

    public UUID getUserId() {
        return userId;
    }
}
