package org.lucoenergia.conluz.domain.admin.community.get;

import org.lucoenergia.conluz.domain.admin.community.Community;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface GetCommunityRepository {

    Optional<Community> findById(UUID id);

    List<Community> findAll();

    List<Community> findAllByIds(Set<UUID> ids);

    Map<UUID, Integer> countMembersByCommunityIds(Set<UUID> ids);

    Map<UUID, Integer> countSuppliesByCommunityIds(Set<UUID> ids);

    Map<UUID, List<String>> findAdminNamesByCommunityIds(Set<UUID> ids);
}
