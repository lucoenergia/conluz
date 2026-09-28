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

    List<SupplyEntity> findByCommunityId(UUID communityId);

    /**
     * Supplies owned by the given user that belong to the given community, unpaginated.
     */
    List<SupplyEntity> findByUserIdAndCommunityId(UUID userId, UUID communityId);

    /**
     * Supplies owned by the given user, eagerly fetching the owning community.
     * <p>
     * The {@code WithCommunity} suffix indicates this method uses {@code LEFT JOIN FETCH s.community}
     * to avoid N+1 lazy-load queries when traversing the community relation, as a caller building
     * response DTOs does.
     *
     * @param userId the owner ID
     * @return the owner's supplies with their community loaded
     */
    @Query("SELECT s FROM supplies s LEFT JOIN FETCH s.community WHERE s.user.id = :userId")
    List<SupplyEntity> findByUserIdWithCommunity(@Param("userId") UUID userId);

    /**
     * Supplies belonging to the given community (paginated), eagerly fetching that community.
     * <p>
     * The count query deliberately omits the fetch join: Spring Data cannot derive a count from a
     * {@code JOIN FETCH} query.
     *
     * @param communityId the community ID
     * @return a page of supplies with their community loaded
     */
    @Query(value = "SELECT s FROM supplies s LEFT JOIN FETCH s.community c WHERE c.id = :communityId",
            countQuery = "SELECT COUNT(s) FROM supplies s WHERE s.community.id = :communityId")
    Page<SupplyEntity> findByCommunityIdWithCommunity(@Param("communityId") UUID communityId, Pageable pageable);

    /**
     * Supplies owned by the given user within the given community (paginated), eagerly fetching that
     * community.
     * <p>
     * The count query deliberately omits the fetch join: Spring Data cannot derive a count from a
     * {@code JOIN FETCH} query.
     *
     * @param userId      the owner ID
     * @param communityId the community ID
     * @return a page of supplies with their community loaded
     */
    @Query(value = "SELECT s FROM supplies s LEFT JOIN FETCH s.community c " +
            "WHERE s.user.id = :userId AND c.id = :communityId",
            countQuery = "SELECT COUNT(s) FROM supplies s " +
                    "WHERE s.user.id = :userId AND s.community.id = :communityId")
    Page<SupplyEntity> findByUserIdAndCommunityIdWithCommunity(@Param("userId") UUID userId,
                                                               @Param("communityId") UUID communityId,
                                                               Pageable pageable);

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
