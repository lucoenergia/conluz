package org.lucoenergia.conluz.infrastructure.admin.community.get;

import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityWithStats;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class GetCommunityServiceImpl implements GetCommunityService {

    private final GetCommunityRepository repository;

    public GetCommunityServiceImpl(GetCommunityRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Community> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<Community> findAll(Set<UUID> visibleIds) {
        if (visibleIds == null) {
            return repository.findAll();
        }
        if (visibleIds.isEmpty()) {
            return List.of();
        }
        return repository.findAllByIds(visibleIds);
    }

    @Override
    public Optional<CommunityWithStats> findByIdWithStats(UUID id) {
        return findById(id)
                .map(community -> enrichWithStats(List.of(community)).get(0));
    }

    @Override
    public List<CommunityWithStats> findAllWithStats(Set<UUID> visibleIds) {
        return enrichWithStats(findAll(visibleIds));
    }

    private List<CommunityWithStats> enrichWithStats(List<Community> communities) {
        if (communities.isEmpty()) {
            return List.of();
        }

        Set<UUID> communityIds = communities.stream()
                .map(Community::getId)
                .collect(Collectors.toSet());

        Map<UUID, Integer> memberCounts = repository.countMembersByCommunityIds(communityIds);
        Map<UUID, Integer> supplyPointCounts = repository.countSuppliesByCommunityIds(communityIds);
        Map<UUID, List<String>> adminNames = repository.findAdminNamesByCommunityIds(communityIds);

        return communities.stream()
                .map(c -> new CommunityWithStats(
                        c,
                        adminNames.getOrDefault(c.getId(), List.of()),
                        memberCounts.getOrDefault(c.getId(), 0),
                        supplyPointCounts.getOrDefault(c.getId(), 0)))
                .toList();
    }
}
