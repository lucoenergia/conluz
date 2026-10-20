package org.lucoenergia.conluz.domain.admin.user.platformadmin;

import org.lucoenergia.conluz.domain.shared.UserId;

/**
 * Use case to grant or revoke the platform-admin flag of a user.
 * <p>
 * The grant operation is idempotent. The revoke operation checks its target, then enforces the last-platform-admin
 * safety rail (the system can never be left without an enabled platform admin). The self-revocation rail (a platform admin
 * cannot revoke their own flag) is enforced at the controller layer, mirroring the enable/disable idiom.
 */
public interface ManagePlatformAdminAccessService {

    void grant(UserId id);

    /**
     * @throws org.lucoenergia.conluz.domain.admin.user.UserNotFoundException if the user does not exist
     * @throws UserNotPlatformAdminException if the user does not hold the platform-admin role
     * @throws LastPlatformAdminException    if the user is the last enabled platform admin
     */
    void revoke(UserId id);
}
