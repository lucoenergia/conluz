package org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

/**
 * The person invited to the platform-admin role already holds it.
 */
public class UserAlreadyPlatformAdminException extends RuntimeException {

    private final UUID userId;

    public UserAlreadyPlatformAdminException(UserId userId) {
        super("User with ID '" + userId.getId() + "' is already a platform admin");
        this.userId = userId.getId();
    }

    public UUID getUserId() {
        return userId;
    }
}
