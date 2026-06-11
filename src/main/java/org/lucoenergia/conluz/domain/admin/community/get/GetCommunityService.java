package org.lucoenergia.conluz.domain.admin.community.get;

import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityWithStats;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface GetCommunityService {

    Optional<Community> findById(UUID id);

    /**
     * @param visibleIds the community ids the caller may see; {@code null} means all communities
     *                   (platform admin), an empty set means none.
     */
    List<Community> findAll(Set<UUID> visibleIds);

    Optional<CommunityWithStats> findByIdWithStats(UUID id);

    /**
     * @param visibleIds the community ids the caller may see; {@code null} means all communities
     *                   (platform admin), an empty set means none.
     */
    List<CommunityWithStats> findAllWithStats(Set<UUID> visibleIds);
}
