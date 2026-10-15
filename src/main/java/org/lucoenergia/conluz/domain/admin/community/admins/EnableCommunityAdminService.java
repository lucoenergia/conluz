package org.lucoenergia.conluz.domain.admin.community.admins;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

/**
 * Enables a community admin's membership of one community. Only the membership is written, never the account, so the
 * person's other memberships are unaffected. Enabling a membership that already is enabled changes nothing.
 */
public interface EnableCommunityAdminService {

    /**
     * @throws org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException if the community does not
     *         exist
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException if the user has no membership
     *         in the community with the community admin role
     */
    void enable(UUID communityId, UserId userId);
}
