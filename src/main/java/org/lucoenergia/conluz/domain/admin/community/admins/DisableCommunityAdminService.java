package org.lucoenergia.conluz.domain.admin.community.admins;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

/**
 * Disables a community admin's membership of one community. Only the membership is written, never the account, so the
 * person's other memberships are unaffected. Disabling a membership that already is disabled changes nothing.
 */
public interface DisableCommunityAdminService {

    /**
     * @param disabledBy the platform admin suspending it
     * @throws org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException if the community does not
     *         exist
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException if the user has no membership
     *         in the community with the community admin role
     */
    void disable(UUID communityId, UserId userId, UserId disabledBy);
}
