package org.lucoenergia.conluz.domain.admin.community.membership.invitation;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.List;
import java.util.UUID;

/**
 * Sends the first invitation to the memberships of a community that are awaiting acceptance, have a contact email
 * and have never been invited, at most {@link #BATCH_SIZE} per call (ADR-0003 §5). Each one is invited as
 * {@link SendInvitationService} does, in a transaction of its own, so a failure keeps the invitations already made;
 * the next call continues with the rest. Every other membership is skipped, not refused.
 *
 * <p>Or, given a selection of at most {@link #BATCH_SIZE} user ids, acts on exactly those memberships: each one is
 * sent its first invitation if it was never invited, or resent it if it was, following the rules of
 * {@link SendInvitationService} and {@link ResendInvitationService}, in a transaction of its own (#527).</p>
 */
public interface BulkSendInvitationService {

    /**
     * The most memberships one call invites, so that a call cannot overflow the email queue.
     */
    int BATCH_SIZE = 50;

    /**
     * @return how many were invited, how many eligible memberships remain, and no results
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipInvitationUnavailableException if the server
     *         cannot send: the public web URL is not configured, or email sending is disabled. Checked first, and
     *         nothing is selected, issued or recorded
     * @param sentBy the community admin who sends them, named in the notification to the community's other admins
     *        for each admin's membership invited (#482)
     */
    SentInvitations send(UUID communityId, UserId sentBy);

    /**
     * Sends or resends the invitation of each selected membership, in ascending user id, each in a transaction of
     * its own. A membership refused by the rules of the send or the resend is answered as refused, and the others
     * are still processed.
     *
     * @param userIds the selection: 1 to {@link #BATCH_SIZE} distinct user ids
     * @return how many were sent their first invitation, how many eligible memberships remain, and one result per
     *         selected user id, in the order given
     * @throws InvitationSelectionRefusedException if the selection is empty, too large or names a user id twice.
     *         Checked first, and nothing is issued or recorded
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipInvitationUnavailableException if the server
     *         cannot send: the public web URL is not configured, or email sending is disabled. Checked once, before
     *         any membership is read, and nothing is issued or recorded
     * @param sentBy the community admin who sends them, named in the notification to the community's other admins
     *        for each admin's membership sent or resent (#482)
     */
    SentInvitations sendTo(UUID communityId, List<UUID> userIds, UserId sentBy);
}
