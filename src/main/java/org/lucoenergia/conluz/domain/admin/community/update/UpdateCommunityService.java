package org.lucoenergia.conluz.domain.admin.community.update;

import org.lucoenergia.conluz.domain.admin.community.Community;

import java.util.UUID;

public interface UpdateCommunityService {

    Community update(UUID id, Community community);
}
