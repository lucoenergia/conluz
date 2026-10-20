package org.lucoenergia.conluz.domain.admin.user.platformadmin;

import org.lucoenergia.conluz.domain.admin.user.User;

/**
 * A user holding the platform-admin role, identified by their own account.
 *
 * @param revocable whether revoking the role from this user would be accepted right now, under
 *                  {@link LastPlatformAdminRule}: false only for the last enabled one. It is state, not authorization: whether the caller may revoke it is
 *                  a separate question.
 */
public record PlatformAdmin(User user, boolean revocable) {
}
