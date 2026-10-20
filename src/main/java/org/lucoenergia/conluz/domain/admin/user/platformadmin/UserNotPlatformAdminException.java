package org.lucoenergia.conluz.domain.admin.user.platformadmin;

import org.lucoenergia.conluz.domain.shared.UserId;

/**
 * Raised when revoking the platform-admin role from a user who does not hold it.
 */
public class UserNotPlatformAdminException extends RuntimeException {

    private final UserId userId;

    public UserNotPlatformAdminException(UserId userId) {
        super("The user is not a platform admin.");
        this.userId = userId;
    }

    public UserId getUserId() {
        return userId;
    }
}
