package org.lucoenergia.conluz.domain.admin.user.platformadmin;

import org.lucoenergia.conluz.domain.admin.user.User;

/**
 * The safety rail that keeps the platform from being left without an enabled platform admin. Revoking the role
 * and telling a client whether a revoke would be accepted both read it from here, so they cannot disagree.
 */
public final class LastPlatformAdminRule {

    private LastPlatformAdminRule() {
    }

    /**
     * Only an enabled platform admin counts towards the rail: revoking a disabled one, or a user without the role,
     * leaves the number of enabled platform admins as it is.
     *
     * @param target                the user whose role would be revoked
     * @param enabledPlatformAdmins how many enabled users hold the platform-admin role
     * @return whether revoking the role from {@code target} must be refused with {@link LastPlatformAdminException}
     */
    public static boolean blocksRevocation(User target, long enabledPlatformAdmins) {
        return target.isPlatformAdmin() && target.isEnabled() && enabledPlatformAdmins <= 1;
    }
}
