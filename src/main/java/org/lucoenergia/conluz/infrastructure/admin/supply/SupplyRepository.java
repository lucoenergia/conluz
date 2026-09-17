package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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

    /*
     * The graph repeated on every finder below is everything SupplyEntityMapper dereferences.
     * Without it each finder returns proxies and the mapper resolves them one row at a time, which
     * is an N+1 on every supply listing. Every path is to-one, so fetch-joining them stays
     * compatible with Pageable: the database still does the paging, unlike a collection join.
     * `plants` is deliberately absent -- it is a @OneToMany, and the mapper never reads it. The list
     * is spelled out at each site because an annotation's attributePaths must be a constant
     * expression, so it cannot be shared through a field.
     */

    @EntityGraph(attributePaths = {"user", "community", "contract", "distributor", "shelly"})
    @Override
    Optional<SupplyEntity> findById(UUID id);

    @EntityGraph(attributePaths = {"user", "community", "contract", "distributor", "shelly"})
    Optional<SupplyEntity> findByCode(String code);

    int countByCode(String code);

    @EntityGraph(attributePaths = {"user", "community", "contract", "distributor", "shelly"})
    List<SupplyEntity> findByUserId(UUID userId);

    @EntityGraph(attributePaths = {"user", "community", "contract", "distributor", "shelly"})
    List<SupplyEntity> findByCommunityId(UUID communityId);

    /**
     * Supplies belonging to the given community (paginated).
     */
    @EntityGraph(attributePaths = {"user", "community", "contract", "distributor", "shelly"})
    Page<SupplyEntity> findByCommunityId(UUID communityId, Pageable pageable);

    /**
     * Supplies owned by the given user that belong to the given community (paginated).
     */
    @EntityGraph(attributePaths = {"user", "community", "contract", "distributor", "shelly"})
    Page<SupplyEntity> findByUserIdAndCommunityId(UUID userId, UUID communityId, Pageable pageable);

    /**
     * Supplies owned by the given user that belong to the given community, unpaginated.
     */
    @EntityGraph(attributePaths = {"user", "community", "contract", "distributor", "shelly"})
    List<SupplyEntity> findByUserIdAndCommunityId(UUID userId, UUID communityId);

    /**
     * Supplies owned by the given user OR belonging to any of the given communities.
     * An empty {@code communityIds} collection effectively restricts the result to owned supplies.
     */
    @EntityGraph(attributePaths = {"user", "community", "contract", "distributor", "shelly"})
    @Query("SELECT s FROM supplies s WHERE s.user.id = :ownerId OR s.community.id IN :communityIds")
    Page<SupplyEntity> findByOwnerOrCommunityIdIn(@Param("ownerId") UUID ownerId,
                                                  @Param("communityIds") Collection<UUID> communityIds,
                                                  Pageable pageable);

    @Query("SELECT s.community.id, COUNT(s) FROM supplies s WHERE s.community.id IN :ids GROUP BY s.community.id")
    List<Object[]> countSuppliesByCommunityIds(@Param("ids") Set<UUID> ids);
}
