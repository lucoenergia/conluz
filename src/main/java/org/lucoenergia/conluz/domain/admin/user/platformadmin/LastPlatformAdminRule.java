package org.lucoenergia.conluz.domain.admin.user.platformadmin;

import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.function.LongSupplier;

/**
 * The safety rail that keeps the platform from being left without an enabled platform admin. Revoking the role,
 * disabling or deleting an account, and telling a client whether a revoke would be accepted all read it from here,
 * so they cannot disagree.
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
        return blocksRevocation(target, () -> enabledPlatformAdmins);
    }

    /**
     * As {@link #blocksRevocation(User, long)}, counting the enabled platform admins only when the target is one of
     * them. Disabling or deleting an account takes the role away from it as revoking does, so the same rule applies.
     */
    public static boolean blocksRevocation(User target, LongSupplier enabledPlatformAdmins) {
        return target.isPlatformAdmin() && target.isEnabled() && enabledPlatformAdmins.getAsLong() <= 1;
    }
}
