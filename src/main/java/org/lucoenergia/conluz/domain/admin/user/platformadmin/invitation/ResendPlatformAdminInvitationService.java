package org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation;

import org.lucoenergia.conluz.domain.shared.UserId;

public interface ResendPlatformAdminInvitationService {

    /**
     * Revokes the invitation's link, issues a new one and a new confirmation code, resets the count of failed code
     * attempts, and emails the new link once the transaction commits. At most one initial send and three resends per
     * invitation in any 24 hours.
     *
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipInvitationUnavailableException if the server
     *         cannot send the email
     * @throws org.lucoenergia.conluz.domain.admin.user.UserNotFoundException if the user does not exist
     * @throws PlatformAdminNotInvitedException if the user has no pending invitation
     * @throws PlatformAdminInvitationResendLimitException if the limit is reached
     */
    InvitedPlatformAdmin resend(UserId userId);
}
