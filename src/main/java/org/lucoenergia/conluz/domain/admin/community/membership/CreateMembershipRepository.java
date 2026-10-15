package org.lucoenergia.conluz.domain.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

public interface CreateMembershipRepository {

    CommunityMembership create(UUID communityId, UUID userId, CommunityRole role);

    /**
     * Creates a pending membership of a registered person: the given role, enabled, not accepted, invited by
     * {@code invitedBy} and not yet sent an invitation. The member record is stored as given.
     *
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException if the user
     *         already has a membership in the community, whatever its state
     */
    CommunityMembership createPending(UUID communityId, UserId userId, MemberRecord record, CommunityRole role,
                                      UserId invitedBy);
}
