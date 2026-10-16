package org.lucoenergia.conluz.domain.admin.community.membership;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

/**
 * Disables a member's membership of one community. Only the membership is written, never the account, which the
 * person shares with their other communities (#415). From the member's next request the membership grants nothing in
 * this community, while their other memberships keep granting what they did. Disabling a membership that already is
 * disabled changes nothing.
 */
public interface DisableMembershipService {

    /**
     * @throws org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException if the community does not
     *         exist
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException if the user has no membership
     *         in the community with the community member role
     */
    void disable(UUID communityId, UserId userId);
}
