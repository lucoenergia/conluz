package org.lucoenergia.conluz.domain.admin.user.platformadmin;

import org.lucoenergia.conluz.domain.shared.UserId;

public interface ManagePlatformAdminRepository {

    void grant(UserId id);

    void revoke(UserId id);

    long countPlatformAdmins();
}
