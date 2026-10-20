package org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

/**
 * The person invited to the platform-admin role already has a pending invitation to it.
 */
public class PlatformAdminAlreadyInvitedException extends RuntimeException {

    private final UUID userId;

    public PlatformAdminAlreadyInvitedException(UserId userId) {
        super("User with ID '" + userId.getId() + "' already has a pending platform-admin invitation");
        this.userId = userId.getId();
    }

    public UUID getUserId() {
        return userId;
    }
}
