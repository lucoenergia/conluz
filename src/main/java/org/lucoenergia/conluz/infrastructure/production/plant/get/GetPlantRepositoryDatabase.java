package org.lucoenergia.conluz.infrastructure.production.plant.get;

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

import java.util.Optional;

@Transactional
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
    public PagedResult<Plant> findAll(PagedRequest pagedRequest) {
        Page<PlantEntity> result = plantRepository.findAll(paginationRequestMapper.mapRequest(pagedRequest));
        return paginationResultMapper.mapResult(result, plantEntityMapper.mapList(result.toList()));
    }
}
