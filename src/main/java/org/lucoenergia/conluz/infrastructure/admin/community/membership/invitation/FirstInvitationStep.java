package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Invites one membership selected by a bulk send, in a transaction of its own, if it is still eligible once locked.
 */
@Component
@Transactional
public class FirstInvitationStep {

    private final InvitationSending sending;

    public FirstInvitationStep(InvitationSending sending) {
        this.sending = sending;
    }

    /**
     * @return whether the membership was invited; false if, once locked, it no longer exists or is no longer
     * eligible, for example because a concurrent request invited it first
     */
    public boolean inviteIfEligible(UUID communityId, UserId userId) {
        Optional<CommunityMembership> membership = sending.lockAndFind(communityId, userId);
        // Re-checked under the lock: the selection was made without it
        if (membership.isEmpty() || !isEligible(membership.get())) {
            return false;
        }
        sending.issueAndSend(membership.get());
        return true;
    }

    private static boolean isEligible(CommunityMembership membership) {
        return membership.isAwaitingAcceptance()
                && membership.getContactEmail() != null
                && membership.getInvitedAt() == null;
    }
}
