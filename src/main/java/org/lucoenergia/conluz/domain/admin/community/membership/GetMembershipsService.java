package org.lucoenergia.conluz.domain.admin.community.membership;


import java.util.List;
import java.util.UUID;

public interface GetMembershipsService {

    /**
     * The community's memberships, each with when its invitation link expires, read for all of them in one query.
     */
    List<MembershipWithInvitation> findByCommunityId(UUID communityId);
}
