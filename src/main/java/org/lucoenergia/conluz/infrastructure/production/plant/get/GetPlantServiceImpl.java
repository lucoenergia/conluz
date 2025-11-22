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

@Transactional(readOnly = true)
@Service
public class GetPlantServiceImpl implements GetPlantService {

    private final GetPlantRepository repository;

    public GetPlantServiceImpl(GetPlantRepository repository) {
        this.repository = repository;
    }

    @Override
    public PagedResult<Plant> findAll(PagedRequest pagedRequest) {

        // If not sorting is provided, sort by descendant order by default
        if (!pagedRequest.isSorted()) {
            final Order defaultOrder = new Order(Direction.ASC, "code");
            pagedRequest.addOrder(defaultOrder);
        }

        return repository.findAll(pagedRequest);
    }

    @Override
    public Plant findById(PlantId id) {
        return repository.findById(id)
                .orElseThrow(() -> new PlantNotFoundException(id));
    }
}