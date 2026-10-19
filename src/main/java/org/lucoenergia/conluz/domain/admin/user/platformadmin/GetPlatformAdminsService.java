package org.lucoenergia.conluz.domain.admin.user.platformadmin;

import java.util.List;

/**
 * The users holding the platform-admin role, and only what identifies them: their own account, never a member
 * record or a membership.
 */
public interface GetPlatformAdminsService {

    /**
     * @return every platform admin, enabled or not, by full name and then id
     */
    List<PlatformAdmin> findAll();
}
