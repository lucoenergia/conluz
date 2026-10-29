package org.lucoenergia.conluz.domain.admin.community.membership.invitation;

/**
 * Why a selection of memberships to invite is refused as a whole, before any of them is invited.
 */
public enum InvitationSelectionProblem {

    /**
     * The selection names no user id.
     */
    EMPTY,
    /**
     * The selection names more than {@link BulkSendInvitationService#BATCH_SIZE} user ids.
     */
    TOO_LARGE,
    /**
     * The selection names a user id more than once.
     */
    DUPLICATED
}
