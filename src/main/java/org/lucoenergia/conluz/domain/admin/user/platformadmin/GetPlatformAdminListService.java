package org.lucoenergia.conluz.domain.admin.user.platformadmin;

import java.util.List;

/**
 * The list of platform admins as a platform admin sees it (ADR-0004 §7): the users holding the role, then the
 * pending invitations to it.
 */
public interface GetPlatformAdminListService {

    /**
     * @return every platform admin as {@link GetPlatformAdminsService#findAll()} returns them, followed by every
     *         pending invitation whose account does not hold the role, by the full name typed by the inviter and
     *         then user id
     */
    List<PlatformAdminListEntry> findAll();
}
