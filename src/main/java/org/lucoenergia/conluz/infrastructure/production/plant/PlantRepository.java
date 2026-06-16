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

    int countByCode(String code);

    Optional<PlantEntity> findByCode(String code);

    /**
     * Plants whose supply belongs to any of the given communities. An empty collection yields an empty page.
     */
    Page<PlantEntity> findBySupplyCommunityIdIn(Collection<UUID> communityIds, Pageable pageable);

    /**
     * Codes of the plants whose supply belongs to the given community. These codes match the
     * {@code station_code} tag used in InfluxDB, so they can be used to scope time-series queries.
     */
    @Query("SELECT p.code FROM plants p WHERE p.supply.community.id = :communityId")
    List<String> findCodesByCommunityId(@Param("communityId") UUID communityId);
}
