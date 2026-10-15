package org.lucoenergia.conluz.domain.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

/**
 * Writes whether a membership is enabled. Only the membership is written: the account, and the person's other
 * memberships, are left as they are.
 */
public interface SetMembershipEnabledRepository {

    /**
     * Sets the user's membership of the community, if it has the given role, to {@code enabled}. Setting the state it
     * already has changes nothing.
     *
     * @return {@code false} if the user has no membership in the community with that role, and nothing was written
     */
    boolean setEnabled(UUID communityId, UserId userId, CommunityRole role, boolean enabled);
}
