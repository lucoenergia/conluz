package org.lucoenergia.conluz.domain.admin.community.get;

import org.lucoenergia.conluz.domain.admin.community.Community;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetCommunityRepository {

    Optional<Community> findById(UUID id);

    List<Community> findAll();
}
