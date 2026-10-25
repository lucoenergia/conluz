package org.lucoenergia.conluz.domain.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

public interface CreateMembershipRepository {

    /**
     * Creates an accepted, enabled membership with no inviter and no invitation, its member record copied from
     * the user. For test fixtures only: no production class may call it, as
     * {@code AcceptedMembershipCreationArchTest} enforces, because every membership is accepted by its member
     * after an invitation (#447).
     */
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
