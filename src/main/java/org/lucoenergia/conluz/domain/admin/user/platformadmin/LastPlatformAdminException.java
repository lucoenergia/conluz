package org.lucoenergia.conluz.domain.admin.user.platformadmin;

/**
 * Raised when any of these actions would leave the system with zero platform admins:
 * - revoking the platform-admin flag.
 * - removing the last platform-admin.
 * - disabling the last platform-admin.
 */
public class LastPlatformAdminException extends RuntimeException {

    public LastPlatformAdminException() {
        super("Cannot perform this action as it would leave the system with zero platform admins.");
    }
}
