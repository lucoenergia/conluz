package org.lucoenergia.conluz.domain.admin.community.update;

import org.lucoenergia.conluz.domain.admin.community.Community;

import java.util.UUID;

public interface UpdateCommunityRepository {

    Community update(UUID id, Community updated);
}
