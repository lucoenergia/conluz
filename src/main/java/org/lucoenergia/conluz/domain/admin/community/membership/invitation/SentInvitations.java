package org.lucoenergia.conluz.domain.admin.community.membership.invitation;

import java.util.List;

/**
 * The outcome of a bulk send of invitations.
 *
 * @param sent      how many memberships were sent their first invitation
 * @param remaining how many memberships of the community are still eligible for a first invitation
 * @param results   one per selected user id, in the order requested; empty when no selection was given
 */
public record SentInvitations(int sent, long remaining, List<InvitationResult> results) {

    public SentInvitations {
        results = List.copyOf(results);
    }
}
