package org.lucoenergia.conluz.domain.admin.community.membership.invitation;

import org.lucoenergia.conluz.domain.shared.UserId;

/**
 * What a bulk send did with one selected membership. Built through its factories, so that a refusal, and only a
 * refusal, carries its reason, and only a refusal for the resend limit carries the wait.
 *
 * @param userId            the user id as requested, whether or not it is a membership of the community
 * @param outcome           sent, resent or refused
 * @param refusal           why it was refused; null unless {@code outcome} is {@link InvitationOutcome#REFUSED}
 * @param retryAfterSeconds the whole seconds until another resend is allowed; null unless {@code refusal} is
 *                          {@link InvitationRefusal#RESEND_LIMIT_REACHED}
 */
public record InvitationResult(UserId userId, InvitationOutcome outcome, InvitationRefusal refusal,
                               Long retryAfterSeconds) {

    public static InvitationResult sent(UserId userId) {
        return new InvitationResult(userId, InvitationOutcome.SENT, null, null);
    }

    public static InvitationResult resent(UserId userId) {
        return new InvitationResult(userId, InvitationOutcome.RESENT, null, null);
    }

    public static InvitationResult refused(UserId userId, InvitationRefusal refusal) {
        return new InvitationResult(userId, InvitationOutcome.REFUSED, refusal, null);
    }

    public static InvitationResult resendLimitReached(UserId userId, long retryAfterSeconds) {
        return new InvitationResult(userId, InvitationOutcome.REFUSED, InvitationRefusal.RESEND_LIMIT_REACHED,
                retryAfterSeconds);
    }
}
