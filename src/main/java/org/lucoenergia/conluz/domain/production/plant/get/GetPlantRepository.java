package org.lucoenergia.conluz.domain.production.plant.get;

import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface GetPlantRepository {

    Optional<Plant> findById(PlantId id);

    PagedResult<Plant> findAll(PagedRequest pagedRequest);

    /**
     * Plants whose supply belongs to any of the given communities.
     */
    PagedResult<Plant> findByCommunities(PagedRequest pagedRequest, Set<UUID> communityIds);
}
