package org.lucoenergia.conluz.domain.production.plant.get;

import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.shared.pagination.Direction;
import org.lucoenergia.conluz.domain.shared.pagination.Order;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.springframework.stereotype.Service;

@Service
public class GetPlantService {

    private final GetPlantRepository repository;

    public GetPlantService(GetPlantRepository repository) {
        this.repository = repository;
    }

    public PagedResult<Plant> findAll(PagedRequest pagedRequest) {

        // If not sorting is provided, sort by descendant order by default
        if (!pagedRequest.isSorted()) {
            final Order defaultOrder = new Order(Direction.ASC, "code");
            pagedRequest.addOrder(defaultOrder);
        }

        return repository.findAll(pagedRequest);
    }
}
