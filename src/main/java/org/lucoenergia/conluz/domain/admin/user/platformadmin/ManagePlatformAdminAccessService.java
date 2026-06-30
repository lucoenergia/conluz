package org.lucoenergia.conluz.domain.admin.user.platformadmin;

import org.lucoenergia.conluz.domain.shared.UserId;

/**
 * Use case to grant or revoke the platform-admin flag of a user.
 * <p>
 * The grant operation is idempotent. The revoke operation enforces the last-platform-admin safety rail
 * (the system can never be left with zero platform admins). The self-revocation rail (a platform admin
 * cannot revoke their own flag) is enforced at the controller layer, mirroring the enable/disable idiom.
 */
public interface ManagePlatformAdminAccessService {

    void grant(UserId id);

    void revoke(UserId id);
}
