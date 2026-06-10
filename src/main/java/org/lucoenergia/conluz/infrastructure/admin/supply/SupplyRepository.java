package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface SupplyRepository extends JpaRepository<SupplyEntity, UUID>, JpaSpecificationExecutor<SupplyEntity> {

    Optional<SupplyEntity> findByCode(String code);

    int countByCode(String code);

    List<SupplyEntity> findByUserId(UUID userId);

    List<SupplyEntity> findByCommunityId(UUID communityId);

    /**
     * Supplies owned by the given user OR belonging to any of the given communities.
     * An empty {@code communityIds} collection effectively restricts the result to owned supplies.
     */
    @Query("SELECT s FROM supplies s WHERE s.user.id = :ownerId OR s.community.id IN :communityIds")
    Page<SupplyEntity> findByOwnerOrCommunityIdIn(@Param("ownerId") UUID ownerId,
                                                  @Param("communityIds") Collection<UUID> communityIds,
                                                  Pageable pageable);

    @Query("SELECT s.community.id, COUNT(s) FROM supplies s WHERE s.community.id IN :ids GROUP BY s.community.id")
    List<Object[]> countSuppliesByCommunityIds(@Param("ids") Set<UUID> ids);
}
