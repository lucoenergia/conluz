package org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation;

import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;

/**
 * Invites a person to the platform-admin role (ADR-0004). Inviting grants nothing: the role is granted only when the
 * person accepts with the link and the confirmation code.
 */
public interface InvitePlatformAdminService {

    /**
     * Finds the account with the personal id, or creates one without a password, never writing an existing one;
     * creates a pending invitation; issues its link and its confirmation code; and emails the link, never the code,
     * once the transaction commits. The answer and the log are the same whether or not the account existed.
     *
     * @throws PlatformAdminInvitationUnavailableException if the server cannot send the email;
     *         nothing is looked up or written
     * @throws org.lucoenergia.conluz.domain.admin.user.UserPersonalIdInvalidException if the personal id is not a
     *         valid DNI, NIE or NIF
     * @throws UserAlreadyPlatformAdminException if the account already holds the role
     * @throws PlatformAdminAlreadyInvitedException if the account already has a pending invitation
     */
    InvitedPlatformAdmin invite(UserId invitedBy, UserPersonalId personalId, String fullName, String email);
}
