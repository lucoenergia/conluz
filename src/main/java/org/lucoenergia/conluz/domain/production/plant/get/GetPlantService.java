package org.lucoenergia.conluz.domain.production.plant.get;

import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;

/**
 * Service for retrieving plant information.
 */
public interface GetPlantService {

    /**
     * Find all plants with pagination.
     *
     * @param pagedRequest the pagination request
     * @return a paged result of plants
     */
    PagedResult<Plant> findAll(PagedRequest pagedRequest);
}