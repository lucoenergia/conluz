package org.lucoenergia.conluz.domain.admin.community.membership.invitation;

/**
 * What a bulk send did with one selected membership.
 */
public enum InvitationOutcome {

    /**
     * Sent its first invitation, as {@link SendInvitationService} does.
     */
    SENT,
    /**
     * Resent its invitation, as {@link ResendInvitationService} does.
     */
    RESENT,
    /**
     * Neither: refused for the {@link InvitationRefusal} given.
     */
    REFUSED
}
