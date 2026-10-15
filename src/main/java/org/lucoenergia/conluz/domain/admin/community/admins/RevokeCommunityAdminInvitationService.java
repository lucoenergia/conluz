package org.lucoenergia.conluz.domain.admin.community.admins;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

/**
 * Revokes a community admin's invitation, with the rules of revoking a member's: the link stops working, and the
 * membership stays pending with its invitation instant.
 */
public interface RevokeCommunityAdminInvitationService {

    /**
     * @throws org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException if the community does not
     *         exist
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException if the user has no membership
     *         in the community with the community admin role
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException if the membership has
     *         been accepted
     */
    CommunityAdmin revoke(UUID communityId, UserId userId);
}
