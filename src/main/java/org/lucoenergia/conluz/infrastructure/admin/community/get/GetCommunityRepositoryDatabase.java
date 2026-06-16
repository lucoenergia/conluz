package org.lucoenergia.conluz.infrastructure.admin.community.get;

import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Transactional
@Repository
public class GetCommunityRepositoryDatabase implements GetCommunityRepository {

    private final CommunityJpaRepository communityJpaRepository;
    private final CommunityMembershipJpaRepository membershipJpaRepository;
    private final SupplyRepository supplyRepository;
    private final CommunityEntityMapper communityEntityMapper;

    public GetCommunityRepositoryDatabase(CommunityJpaRepository communityJpaRepository,
                                          CommunityMembershipJpaRepository membershipJpaRepository,
                                          SupplyRepository supplyRepository,
                                          CommunityEntityMapper communityEntityMapper) {
        this.communityJpaRepository = communityJpaRepository;
        this.membershipJpaRepository = membershipJpaRepository;
        this.supplyRepository = supplyRepository;
        this.communityEntityMapper = communityEntityMapper;
    }

    @Override
    public Optional<Community> findById(UUID id) {
        Optional<CommunityEntity> entity = communityJpaRepository.findById(id);
        return entity.map(communityEntityMapper::map);
    }

    @Override
    public List<Community> findAll() {
        return communityEntityMapper.mapList(communityJpaRepository.findAll());
    }

    @Override
    public Set<UUID> findAllIds() {
        return new HashSet<>(communityJpaRepository.findAllIds());
    }

    @Override
    public List<Community> findAllByIds(Set<UUID> ids) {
        return communityEntityMapper.mapList(communityJpaRepository.findAllById(ids));
    }

    @Override
    public Map<UUID, Integer> countMembersByCommunityIds(Set<UUID> ids) {
        List<Object[]> rows = membershipJpaRepository.countMembersByCommunityIds(ids);
        Map<UUID, Integer> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return map;
    }

    @Override
    public Map<UUID, Integer> countSuppliesByCommunityIds(Set<UUID> ids) {
        List<Object[]> rows = supplyRepository.countSuppliesByCommunityIds(ids);
        Map<UUID, Integer> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return map;
    }

    @Override
    public Map<UUID, List<String>> findAdminNamesByCommunityIds(Set<UUID> ids) {
        List<Object[]> rows = membershipJpaRepository.findAdminNamesByCommunityIds(ids);
        Map<UUID, List<String>> map = new HashMap<>();
        for (Object[] row : rows) {
            UUID communityId = (UUID) row[0];
            String adminName = (String) row[1];
            map.computeIfAbsent(communityId, k -> new ArrayList<>()).add(adminName);
        }
        return map;
    }
}
