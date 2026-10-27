package org.lucoenergia.conluz.domain.admin.community.membership.invitation;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

/**
 * Sends the first invitation to the memberships of a community that are awaiting acceptance, have a contact email
 * and have never been invited, at most {@link #BATCH_SIZE} per call (ADR-0003 §5). Each one is invited as
 * {@link SendInvitationService} does, in a transaction of its own, so a failure keeps the invitations already made;
 * the next call continues with the rest. Every other membership is skipped, not refused.
 */
public interface BulkSendInvitationService {

    /**
     * The most memberships one call invites, so that a call cannot overflow the email queue.
     */
    int BATCH_SIZE = 50;

    /**
     * @return how many were invited, and how many eligible memberships remain
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipInvitationUnavailableException if the server
     *         cannot send: the public web URL is not configured, or email sending is disabled. Checked first, and
     *         nothing is selected, issued or recorded
     * @param sentBy the community admin who sends them, named in the notification to the community's other admins
     *        for each admin's membership invited (#482)
     */
    SentInvitations send(UUID communityId, UserId sentBy);
}
