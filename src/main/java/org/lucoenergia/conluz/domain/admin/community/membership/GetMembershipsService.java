package org.lucoenergia.conluz.domain.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;

import java.util.List;
import java.util.UUID;

public interface GetMembershipsService {

    List<CommunityMembership> findByCommunityId(UUID communityId);
}
