package org.lucoenergia.conluz.domain.production.plant.get;

import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;

import java.util.List;
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

    /**
     * The single plant of a community. Relies on the one-plant-per-community invariant enforced by
     * the phase 2d migration precondition; if that invariant is ever violated, the underlying query
     * throws rather than silently picking one plant.
     */
    Optional<Plant> findByCommunityId(UUID communityId);

    /**
     * Provider codes of the plants belonging to the given community. These codes match the InfluxDB
     * {@code station_code} tag, so they can be used to scope time-series production queries.
     */
    List<String> findPlantProviderCodesByCommunity(UUID communityId);

    /**
     * Supply codes (CUPS) of the plants belonging to the given community. Used to detect which
     * supplies back a plant so their Datadis surplus can be derived into production.
     */
    Set<String> findSupplyCodesByCommunity(UUID communityId);
}
