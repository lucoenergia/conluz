package org.lucoenergia.conluz.domain.admin.community.membership.invitation;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.time.Instant;
import java.util.UUID;

public interface UpdateMembershipInvitationRepository {

    /**
     * Records when the user's membership in the community was last sent an invitation.
     *
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException if there is no such
     *         membership
     */
    CommunityMembership markInvited(UserId userId, UUID communityId, Instant invitedAt);
}
