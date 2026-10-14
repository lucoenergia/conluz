package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface HuaweiConfigRepository extends JpaRepository<HuaweiConfigEntity, UUID> {

    Optional<HuaweiConfigEntity> findFirstBy();

    Optional<HuaweiConfigEntity> findByPlantId(UUID plantId);

    java.util.List<HuaweiConfigEntity> findAllByEnabledTrue();

    /**
     * Writes {@code enabled} and no other column. Flushes pending changes first and clears the persistence
     * context after, so no managed entity holds a stale value. Returns the number of rows updated: at most one,
     * since plant_id is unique.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update huawei_config c set c.enabled = :enabled where c.plant.id = :plantId")
    int updateEnabledByPlantId(@Param("plantId") UUID plantId, @Param("enabled") boolean enabled);
}
