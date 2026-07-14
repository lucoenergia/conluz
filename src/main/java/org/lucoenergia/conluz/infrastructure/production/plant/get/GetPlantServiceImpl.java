package org.lucoenergia.conluz.infrastructure.production.plant.get;

import org.lucoenergia.conluz.domain.production.plant.get.GetPlantService;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.pagination.Direction;
import org.lucoenergia.conluz.domain.shared.pagination.Order;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Transactional(readOnly = true)
@Service
public class GetPlantServiceImpl implements GetPlantService {

    private final GetPlantRepository repository;

    public GetPlantServiceImpl(GetPlantRepository repository) {
        this.repository = repository;
    }

    PagedResult<Plant> findAll(PagedRequest pagedRequest) {
        applyDefaultSort(pagedRequest);
        return repository.findAll(pagedRequest);
    }

    @Override
    public PagedResult<Plant> findAllByCommunities(PagedRequest pagedRequest, Set<UUID> communityIds) {
        if (communityIds == null) {
            return findAll(pagedRequest);
        }
        applyDefaultSort(pagedRequest);
        return repository.findByCommunities(pagedRequest, communityIds);
    }

    private void applyDefaultSort(PagedRequest pagedRequest) {
        // If no sorting is provided, sort by provider code ascending by default
        if (!pagedRequest.isSorted()) {
            final Order defaultOrder = new Order(Direction.ASC, "providerCode");
            pagedRequest.addOrder(defaultOrder);
        }
    }

    @Override
    public Plant findById(PlantId id) {
        return repository.findById(id)
                .orElseThrow(() -> new PlantNotFoundException(id));
    }
}