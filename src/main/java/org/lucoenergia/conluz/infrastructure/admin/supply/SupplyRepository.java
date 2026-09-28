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

    /**
     * The associations {@code SupplyEntityMapper} traverses for every supply it maps. Without them a
     * query returning N supplies costs a further query per supply -- a cost the scheduled jobs that
     * iterate every supply pay in full. All five are to-one, so fetching them cannot multiply rows
     * and stays compatible with pagination.
     * <p>
     * Binds the aliases {@code c} (community) and {@code u} (user) for use in the where clause.
     */
    String MAPPED_ASSOCIATIONS = "LEFT JOIN FETCH s.community c LEFT JOIN FETCH s.user u " +
            "LEFT JOIN FETCH s.shelly LEFT JOIN FETCH s.distributor LEFT JOIN FETCH s.contract";

    Optional<SupplyEntity> findByCode(String code);

    int countByCode(String code);

    /**
     * Every supply (paginated), with the mapped associations fetched.
     * <p>
     * The count query deliberately omits the fetch joins: Spring Data cannot derive a count from a
     * {@code JOIN FETCH} query.
     */
    @Query(value = "SELECT s FROM supplies s " + MAPPED_ASSOCIATIONS,
            countQuery = "SELECT COUNT(s) FROM supplies s")
    Page<SupplyEntity> findAllWithAssociations(Pageable pageable);

    /**
     * Supplies owned by the given user, with the mapped associations fetched.
     */
    @Query("SELECT s FROM supplies s " + MAPPED_ASSOCIATIONS + " WHERE u.id = :userId")
    List<SupplyEntity> findByUserIdWithAssociations(@Param("userId") UUID userId);

    /**
     * Supplies belonging to the given community, with the mapped associations fetched.
     */
    @Query("SELECT s FROM supplies s " + MAPPED_ASSOCIATIONS + " WHERE c.id = :communityId")
    List<SupplyEntity> findByCommunityIdWithAssociations(@Param("communityId") UUID communityId);

    /**
     * Supplies belonging to the given community (paginated), with the mapped associations fetched.
     */
    @Query(value = "SELECT s FROM supplies s " + MAPPED_ASSOCIATIONS + " WHERE c.id = :communityId",
            countQuery = "SELECT COUNT(s) FROM supplies s WHERE s.community.id = :communityId")
    Page<SupplyEntity> findByCommunityIdWithAssociations(@Param("communityId") UUID communityId, Pageable pageable);

    /**
     * Supplies owned by the given user within the given community, with the mapped associations
     * fetched.
     */
    @Query("SELECT s FROM supplies s " + MAPPED_ASSOCIATIONS + " WHERE u.id = :userId AND c.id = :communityId")
    List<SupplyEntity> findByUserIdAndCommunityIdWithAssociations(@Param("userId") UUID userId,
                                                                  @Param("communityId") UUID communityId);

    /**
     * Supplies owned by the given user within the given community (paginated), with the mapped
     * associations fetched.
     */
    @Query(value = "SELECT s FROM supplies s " + MAPPED_ASSOCIATIONS + " WHERE u.id = :userId AND c.id = :communityId",
            countQuery = "SELECT COUNT(s) FROM supplies s WHERE s.user.id = :userId AND s.community.id = :communityId")
    Page<SupplyEntity> findByUserIdAndCommunityIdWithAssociations(@Param("userId") UUID userId,
                                                                  @Param("communityId") UUID communityId,
                                                                  Pageable pageable);

    /**
     * Supplies with any of the given ids, with the mapped associations fetched.
     */
    @Query("SELECT s FROM supplies s " + MAPPED_ASSOCIATIONS + " WHERE s.id IN :ids")
    List<SupplyEntity> findByIdInWithAssociations(@Param("ids") Collection<UUID> ids);

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
