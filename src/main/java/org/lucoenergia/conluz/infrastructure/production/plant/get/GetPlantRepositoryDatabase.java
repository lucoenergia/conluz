package org.lucoenergia.conluz.infrastructure.production.plant.get;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.PlantReference;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntityMapper;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.shared.pagination.PaginationRequestMapper;
import org.lucoenergia.conluz.infrastructure.shared.pagination.PaginationResultMapper;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Repository
public class GetPlantRepositoryDatabase implements GetPlantRepository {

    private final PlantRepository plantRepository;
    private final PlantEntityMapper plantEntityMapper;
    private final PaginationRequestMapper paginationRequestMapper;
    private final PaginationResultMapper<PlantEntity, Plant> paginationResultMapper;

    public GetPlantRepositoryDatabase(PlantRepository plantRepository, PlantEntityMapper plantEntityMapper,
                                      PaginationRequestMapper paginationRequestMapper,
                                      PaginationResultMapper<PlantEntity, Plant> paginationResultMapper) {
        this.plantRepository = plantRepository;
        this.plantEntityMapper = plantEntityMapper;
        this.paginationRequestMapper = paginationRequestMapper;
        this.paginationResultMapper = paginationResultMapper;
    }

    @Override
    public Optional<Plant> findById(PlantId id) {
        Optional<PlantEntity> entity = plantRepository.findById(id.getId());
        return entity.map(plantEntityMapper::map);
    }

    @Override
    public List<Plant> findAllByIds(Collection<PlantId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return plantEntityMapper.mapList(plantRepository.findAllByIdIn(ids.stream().map(PlantId::getId).toList()));
    }

    @Override
    public PagedResult<Plant> findAll(PagedRequest pagedRequest) {
        Page<PlantEntity> result = plantRepository.findAll(paginationRequestMapper.mapRequest(pagedRequest));
        return paginationResultMapper.mapResult(result, plantEntityMapper.mapList(result.toList()));
    }

    @Override
    public PagedResult<Plant> findByCommunities(PagedRequest pagedRequest, Set<UUID> communityIds) {
        Page<PlantEntity> result = plantRepository.findBySupplyCommunityIdIn(communityIds,
                paginationRequestMapper.mapRequest(pagedRequest));
        return paginationResultMapper.mapResult(result, plantEntityMapper.mapList(result.toList()));
    }

    @Override
    public Optional<Plant> findByCommunityId(UUID communityId) {
        return plantRepository.findBySupplyCommunityId(communityId).map(plantEntityMapper::map);
    }

    @Override
    public List<String> findPlantProviderCodesByCommunity(UUID communityId) {
        return plantRepository.findProviderCodesByCommunityId(communityId);
    }

    @Override
    public Set<String> findSupplyCodesByCommunity(UUID communityId) {
        return new HashSet<>(plantRepository.findSupplyCodesByCommunityId(communityId));
    }

    @Override
    public boolean existsBySupply(SupplyId supplyId) {
        return plantRepository.existsBySupplyId(supplyId.getId());
    }

    @Override
    public Map<UUID, PlantReference> findReferencesBySupplies(Collection<SupplyId> supplyIds) {
        if (supplyIds.isEmpty()) {
            return Map.of();
        }
        Set<UUID> ids = supplyIds.stream().map(SupplyId::getId).collect(Collectors.toSet());
        return plantRepository.findReferencesBySupplyIdIn(ids).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> new PlantReference((UUID) row[1], (String) row[2])));
    }
}
