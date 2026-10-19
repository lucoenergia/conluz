package org.lucoenergia.conluz.domain.admin.user.platformadmin;

import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.List;

public interface GetPlatformAdminsRepository {

    /**
     * @return every user holding the platform-admin role, enabled or not, by full name and then id
     */
    List<User> findAll();
}
