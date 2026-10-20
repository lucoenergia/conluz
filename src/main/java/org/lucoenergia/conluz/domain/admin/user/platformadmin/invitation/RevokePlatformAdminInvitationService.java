package org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation;

import org.lucoenergia.conluz.domain.admin.user.platformadmin.PendingPlatformAdmin;
import org.lucoenergia.conluz.domain.shared.UserId;

public interface RevokePlatformAdminInvitationService {

    /**
     * Revokes the invitation's link. The invitation stays pending, and can be sent again.
     *
     * @return the invitation's row, without a valid link
     * @throws org.lucoenergia.conluz.domain.admin.user.UserNotFoundException if the user does not exist
     * @throws PlatformAdminNotInvitedException if the user has no pending invitation
     */
    PendingPlatformAdmin revoke(UserId userId);
}
