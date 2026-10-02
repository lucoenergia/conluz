package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.*;

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

    @EntityGraph(attributePaths = {"user", "community", "contract", "distributor", "shelly"})
    Optional<SupplyEntity> findByCode(String code);

    int countByCode(String code);

    @EntityGraph(attributePaths = {"user", "community", "contract", "distributor", "shelly"})
    List<SupplyEntity> findByUserId(UUID userId);

    @EntityGraph(attributePaths = {"user", "community", "contract", "distributor", "shelly"})
    List<SupplyEntity> findByCommunityId(UUID communityId);

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
     * Supplies belonging to the given community (paginated), with the mapped associations fetched.
     */
    @EntityGraph(attributePaths = {"user", "community", "contract", "distributor", "shelly"})
    Page<SupplyEntity> findByCommunityId(UUID communityId, Pageable pageable);

    /**
     * Supplies owned by the given user, with the mapped associations fetched.
     */
    @Query("SELECT s FROM supplies s " + MAPPED_ASSOCIATIONS + " WHERE u.id = :userId")
    List<SupplyEntity> findByUserIdWithAssociations(@Param("userId") UUID userId);

    /**
     * Supplies owned by the given user within the given community (paginated), with the mapped
     * associations fetched.
     */
    @EntityGraph(attributePaths = {"user", "community", "contract", "distributor", "shelly"})
    Page<SupplyEntity> findByUserIdAndCommunityId(UUID userId, UUID communityId, Pageable pageable);

    /**
     * Supplies belonging to the given community, with the mapped associations fetched.
     */
    @Query("SELECT s FROM supplies s " + MAPPED_ASSOCIATIONS + " WHERE c.id = :communityId")
    List<SupplyEntity> findByCommunityIdWithAssociations(@Param("communityId") UUID communityId);

    /**
     * Supplies owned by the given user within the given community, with the mapped associations
     * fetched.
     */
    @EntityGraph(attributePaths = {"user", "community", "contract", "distributor", "shelly"})
    List<SupplyEntity> findByUserIdAndCommunityId(UUID userId, UUID communityId);

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
     * Supplies owned by the given user within any of the given communities, with the mapped
     * associations fetched. One statement however many communities there are. {@code communityIds}
     * must not be empty: {@code IN ()} is not valid SQL.
     */
    @Query("SELECT s FROM supplies s " + MAPPED_ASSOCIATIONS + " WHERE u.id = :userId AND c.id IN :communityIds")
    List<SupplyEntity> findByUserIdAndCommunityIdInWithAssociations(@Param("userId") UUID userId,
                                                                    @Param("communityIds") Collection<UUID> communityIds);

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
    @EntityGraph(attributePaths = {"user", "community", "contract", "distributor", "shelly"})
    @Query("SELECT s FROM supplies s WHERE s.user.id = :ownerId OR s.community.id IN :communityIds")
    Page<SupplyEntity> findByOwnerOrCommunityIdIn(@Param("ownerId") UUID ownerId,
                                                  @Param("communityIds") Collection<UUID> communityIds,
                                                  Pageable pageable);

    @Query("SELECT s.community.id, COUNT(s) FROM supplies s WHERE s.community.id IN :ids GROUP BY s.community.id")
    List<Object[]> countSuppliesByCommunityIds(@Param("ids") Set<UUID> ids);
}
