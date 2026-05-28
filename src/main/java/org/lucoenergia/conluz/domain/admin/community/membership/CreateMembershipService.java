package org.lucoenergia.conluz.domain.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;

import java.util.UUID;

public interface CreateMembershipService {

    CommunityMembership create(UUID communityId, UUID userId, CommunityRole role);
}
