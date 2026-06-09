package org.lucoenergia.conluz.domain.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;

import java.util.UUID;

public interface CreateMembershipRepository {

    CommunityMembership create(UUID communityId, UUID userId, CommunityRole role);
}
