package org.lucoenergia.conluz.domain.admin.community.membership.invitation;

/**
 * Why a bulk send refused one selected membership: the refusals of {@link SendInvitationService} and
 * {@link ResendInvitationService} that a membership chosen between them can meet.
 */
public enum InvitationRefusal {

    /**
     * The id is not a membership of the community: an unknown account and another community's member alike.
     */
    NOT_A_MEMBER,
    /**
     * The membership has been accepted.
     */
    ALREADY_ACCEPTED,
    /**
     * The pending membership is disabled.
     */
    DISABLED,
    /**
     * The member record has no contact email.
     */
    WITHOUT_CONTACT_EMAIL,
    /**
     * The invitation has been sent as many times as allowed in the last 24 hours.
     */
    RESEND_LIMIT_REACHED
}
