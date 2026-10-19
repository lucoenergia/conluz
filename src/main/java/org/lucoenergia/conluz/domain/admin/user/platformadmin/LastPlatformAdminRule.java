package org.lucoenergia.conluz.domain.admin.user.platformadmin;

/**
 * The safety rail that keeps the platform from being left without an enabled platform admin. Revoking the role
 * and telling a client whether a revoke would be accepted both read it from here, so they cannot disagree.
 */
public final class LastPlatformAdminRule {

    private LastPlatformAdminRule() {
    }

    /**
     * @param enabledPlatformAdmins how many enabled users hold the platform-admin role
     * @return whether revoking the role from a user must be refused with {@link LastPlatformAdminException}
     */
    public static boolean blocksRevocation(long enabledPlatformAdmins) {
        return enabledPlatformAdmins <= 1;
    }
}
