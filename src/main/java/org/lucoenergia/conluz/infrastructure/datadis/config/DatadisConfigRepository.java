package org.lucoenergia.conluz.infrastructure.datadis.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DatadisConfigRepository extends JpaRepository<DatadisConfigEntity, UUID> {

    Optional<DatadisConfigEntity> findFirstBy();

    Optional<DatadisConfigEntity> findByCommunityId(UUID communityId);

    java.util.List<DatadisConfigEntity> findAllByEnabledTrue();

    /**
     * Writes {@code enabled} and no other column. Flushes pending changes first and clears the persistence
     * context after, so no managed entity holds a stale value. Returns the number of rows updated: at most one,
     * since community_id is unique.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update datadis_config c set c.enabled = :enabled where c.community.id = :communityId")
    int updateEnabledByCommunityId(@Param("communityId") UUID communityId, @Param("enabled") boolean enabled);
}
