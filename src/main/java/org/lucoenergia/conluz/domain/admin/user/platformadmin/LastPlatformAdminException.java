package org.lucoenergia.conluz.domain.admin.user.platformadmin;

/**
 * Raised when revoking the platform-admin flag would leave the system with zero platform admins.
 */
public class LastPlatformAdminException extends RuntimeException {

    public LastPlatformAdminException() {
        super("Cannot revoke the last platform administrator.");
    }
}
