package org.lucoenergia.conluz.domain.admin.community.membership.invitation;

import org.lucoenergia.conluz.domain.admin.community.membership.MembershipWithInvitation;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

/**
 * Sends the invitation of a pending membership again (ADR-0003 §5): issues a new {@code INVITATION} token, which
 * revokes the previous one whether it was valid, expired or revoked, records the resend's instant as the invitation
 * instant and emails the link to the member record's current contact email once the transaction commits. A membership
 * is sent at most 4 invitations in any 24 hours, the first included.
 */
public interface ResendInvitationService {

    /**
     * @return the membership, with its new invitation instant and when the link sent expires
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipInvitationUnavailableException if the server
     *         cannot send it: the public web URL is not configured, or email sending is disabled. Checked first, and
     *         nothing is issued or recorded
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException if the user has no
     *         membership in the community
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException if the membership has
     *         been accepted
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipDisabledException if the pending membership
     *         is disabled
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipNotInvitedException if the membership has never
     *         been invited: its first invitation is sent with {@link SendInvitationService}
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipWithoutContactEmailException if the member
     *         record has no contact email
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipInvitationResendLimitException if 4 invitations
     *         have been issued for the membership in the last 24 hours
     * @param resentBy the community admin who resends it, named in the notification to the community's other admins
     *        when the membership is an admin's (#482)
     */
    MembershipWithInvitation resend(UUID communityId, UserId userId, UserId resentBy);
}
