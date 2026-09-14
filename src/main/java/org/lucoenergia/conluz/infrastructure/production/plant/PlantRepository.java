package org.lucoenergia.conluz.infrastructure.production.plant;

import org.lucoenergia.conluz.domain.production.InverterProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlantRepository extends JpaRepository<PlantEntity, UUID> {

    List<PlantEntity> findAllByInverterProvider(InverterProvider provider);

    int countByProviderCode(String providerCode);

    Optional<PlantEntity> findByProviderCode(String providerCode);

    /**
     * Plants whose supply belongs to any of the given communities. An empty collection yields an empty page.
     */
    Page<PlantEntity> findBySupplyCommunityIdIn(Collection<UUID> communityIds, Pageable pageable);

    /**
     * The single plant whose supply belongs to the given community. Throws
     * {@link org.springframework.dao.IncorrectResultSizeDataAccessException} if more than one plant
     * matches, which should never happen under the one-plant-per-community invariant relied on by
     * the phase 2d migration.
     */
    Optional<PlantEntity> findBySupplyCommunityId(UUID communityId);

    /**
     * Provider codes of the plants whose supply belongs to the given community. These codes match
     * the {@code station_code} tag used in InfluxDB, so they can be used to scope time-series queries.
     */
    @Query("SELECT p.providerCode FROM plants p WHERE p.supply.community.id = :communityId")
    List<String> findProviderCodesByCommunityId(@Param("communityId") UUID communityId);

    /**
     * Supply codes (CUPS) of the plants whose supply belongs to the given community. Unlike
     * {@link #findProviderCodesByCommunityId(UUID)} (the plant/station provider code), this returns
     * the CUPS used as the {@code cups} tag in the Datadis measurements.
     */
    @Query("SELECT p.supply.code FROM plants p WHERE p.supply.community.id = :communityId")
    List<String> findSupplyCodesByCommunityId(@Param("communityId") UUID communityId);
}
