package org.lucoenergia.conluz.domain.production.plant.get;

import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;

import java.util.Set;
import java.util.UUID;

/**
 * Service for retrieving plant information.
 */
public interface GetPlantService {

    /**
     * Find plants visible to a caller scoped to the given communities. A {@code null}
     * {@code communityIds} means no restriction (all plants); an empty set yields no plants.
     *
     * @param pagedRequest the pagination request
     * @param communityIds the communities the caller may see (null = all)
     * @return a paged result of plants
     */
    PagedResult<Plant> findAllByCommunities(PagedRequest pagedRequest, Set<UUID> communityIds);

    /**
     * Find a plant by its ID.
     *
     * @param id the plant ID
     * @return the plant
     */
    Plant findById(PlantId id);
}