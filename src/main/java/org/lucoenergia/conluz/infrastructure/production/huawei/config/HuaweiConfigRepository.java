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
     * Locks the plant's configuration row until the end of the current transaction. {@code FOR NO KEY UPDATE}
     * conflicts with the lock {@link #updateEnabledByPlantId} takes, so a concurrent enable or disable waits for
     * the transaction holding this one.
     *
     * @return the configuration's id, or empty if the plant has no configuration
     */
    @Query(value = "SELECT id FROM huawei_config WHERE plant_id = :plantId FOR NO KEY UPDATE", nativeQuery = true)
    Optional<UUID> lockByPlantId(@Param("plantId") UUID plantId);

    /**
     * Writes {@code enabled} and no other column. Flushes pending changes first and clears the persistence
     * context after, so no managed entity holds a stale value. Returns the number of rows updated: at most one,
     * since plant_id is unique.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update huawei_config c set c.enabled = :enabled where c.plant.id = :plantId")
    int updateEnabledByPlantId(@Param("plantId") UUID plantId, @Param("enabled") boolean enabled);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from huawei_config c where c.plant.id = :plantId")
    void deleteByPlantId(@Param("plantId") UUID plantId);
}
