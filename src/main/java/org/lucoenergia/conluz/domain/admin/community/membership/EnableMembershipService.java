package org.lucoenergia.conluz.domain.admin.community.membership;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

/**
 * Enables a member's membership of one community. Only the membership is written, never the account, which the person
 * shares with their other communities (#415). Enabling a membership that already is enabled changes
 * nothing.
 */
public interface EnableMembershipService {

    /**
     * @throws org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException if the community does not
     *         exist
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException if the user has no membership
     *         in the community with the community member role
     */
    void enable(UUID communityId, UserId userId);
}
