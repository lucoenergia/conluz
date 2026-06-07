package org.lucoenergia.conluz.domain.admin.community.get;

import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityWithStats;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetCommunityService {

    Optional<Community> findById(UUID id);

    List<Community> findAllVisible();

    Optional<CommunityWithStats> findByIdWithStats(UUID id);

    List<CommunityWithStats> findAllVisibleWithStats();
}
