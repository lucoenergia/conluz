package org.lucoenergia.conluz.domain.admin.user.platformadmin;

import org.lucoenergia.conluz.domain.shared.UserId;

/**
 * Use case to revoke the platform-admin flag of a user. The flag is granted only by accepting a platform-admin
 * invitation, or to the first admin by {@code /init}.
 * <p>
 * The revoke operation checks its target, then enforces the last-platform-admin
 * safety rail (the system can never be left without an enabled platform admin). The self-revocation rail (a platform admin
 * cannot revoke their own flag) is enforced at the controller layer, mirroring the enable/disable idiom.
 */
public interface ManagePlatformAdminAccessService {

    /**
     * @throws org.lucoenergia.conluz.domain.admin.user.UserNotFoundException if the user does not exist
     * @throws UserNotPlatformAdminException if the user does not hold the platform-admin role
     * @throws LastPlatformAdminException    if the user is the last enabled platform admin
     */
    void revoke(UserId id);
}
