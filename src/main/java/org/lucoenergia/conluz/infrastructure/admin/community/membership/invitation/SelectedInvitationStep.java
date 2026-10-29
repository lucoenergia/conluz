package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.InvitationOutcome;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.ResendInvitationService;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.SendInvitationService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Sends or resends the invitation of one membership selected for a bulk send, in a transaction of its own (#527).
 *
 * <p>The send and the resend are {@link SendInvitationService} and {@link ResendInvitationService} themselves, joined
 * to this transaction, so their rules, their limit and their notification of an admin's membership are the single
 * endpoints'. A refusal is thrown out of this step, which rolls its transaction back.</p>
 */
@Component
@Transactional
public class SelectedInvitationStep {

    private final InvitationSending sending;
    private final SendInvitationService sendInvitationService;
    private final ResendInvitationService resendInvitationService;

    public SelectedInvitationStep(InvitationSending sending, SendInvitationService sendInvitationService,
                                  ResendInvitationService resendInvitationService) {
        this.sending = sending;
        this.sendInvitationService = sendInvitationService;
        this.resendInvitationService = resendInvitationService;
    }

    /**
     * @return {@link InvitationOutcome#SENT} or {@link InvitationOutcome#RESENT}
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException if the user has no
     *         membership in the community; otherwise, any refusal of the send or the resend
     */
    public InvitationOutcome sendOrResend(UUID communityId, UserId userId, UserId sentBy) {
        // Locked before choosing, so the choice holds for the send or resend that follows in this transaction:
        // a membership, once invited, is never uninvited
        CommunityMembership membership = sending.lockAndRead(communityId, userId);
        if (membership.getInvitedAt() == null) {
            sendInvitationService.send(communityId, userId, sentBy);
            return InvitationOutcome.SENT;
        }
        resendInvitationService.resend(communityId, userId, sentBy);
        return InvitationOutcome.RESENT;
    }
}
