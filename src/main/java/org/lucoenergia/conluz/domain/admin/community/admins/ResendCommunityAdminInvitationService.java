package org.lucoenergia.conluz.domain.admin.community.admins;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

/**
 * Sends a community admin's invitation again, with the rules of resending a member's: a new link that stops the
 * previous one, at most 4 sends per membership in any 24 hours.
 */
public interface ResendCommunityAdminInvitationService {

    /**
     * @param resentBy the platform admin resending it
     * @throws org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException if the community does not
     *         exist
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException if the user has no membership
     *         in the community with the community admin role
     * @see org.lucoenergia.conluz.domain.admin.community.membership.invitation.ResendInvitationService for the other
     *         refusals
     */
    CommunityAdmin resend(UUID communityId, UserId userId, UserId resentBy);
}
