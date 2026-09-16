package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientDetail;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplyPartitionCoefficientJpaRepository extends JpaRepository<SupplyPartitionCoefficientEntity, UUID> {

    // valid_from inclusive, valid_to exclusive; scoped to a single plant, unambiguous when a supply
    // has concurrently-active coefficients across multiple plants
    @Query("SELECT e FROM SupplyPartitionCoefficientEntity e WHERE e.plant.id = :plantId " +
            "AND e.supply.id = :supplyId AND e.validFrom <= :timestamp AND (e.validTo IS NULL OR e.validTo > :timestamp)")
    Optional<SupplyPartitionCoefficientEntity> findByPlantIdAndSupplyIdAtTimestamp(
            @Param("plantId") UUID plantId,
            @Param("supplyId") UUID supplyId,
            @Param("timestamp") Instant timestamp);

    // Every plant's coefficient for this supply active at timestamp (valid_from inclusive, valid_to exclusive)
    @Query("SELECT e FROM SupplyPartitionCoefficientEntity e WHERE e.supply.id = :supplyId " +
            "AND e.validFrom <= :timestamp AND (e.validTo IS NULL OR e.validTo > :timestamp)")
    List<SupplyPartitionCoefficientEntity> findAllBySupplyIdAtTimestamp(
            @Param("supplyId") UUID supplyId,
            @Param("timestamp") Instant timestamp);

    // Periods overlapping [from, to): period starts before to AND (period is open OR ends after from)
    @Query("SELECT e FROM SupplyPartitionCoefficientEntity e WHERE e.supply.id = :supplyId " +
            "AND e.validFrom < :to AND (e.validTo IS NULL OR e.validTo > :from) " +
            "ORDER BY e.validFrom ASC")
    List<SupplyPartitionCoefficientEntity> findBySupplyIdInRange(
            @Param("supplyId") UUID supplyId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("SELECT e FROM SupplyPartitionCoefficientEntity e WHERE e.supply.id = :supplyId ORDER BY e.validFrom ASC")
    List<SupplyPartitionCoefficientEntity> findAllBySupplyIdOrderByValidFromAsc(@Param("supplyId") UUID supplyId);

    @Query("SELECT e FROM SupplyPartitionCoefficientEntity e WHERE e.validFrom <= :timestamp " +
            "AND (e.validTo IS NULL OR e.validTo > :timestamp)")
    List<SupplyPartitionCoefficientEntity> findAllActiveAtTimestamp(@Param("timestamp") Instant timestamp);

    // The earliest activation among the coefficients of a community's plants, in one aggregate
    // query. A plant has no community column of its own -- it belongs to one through its own
    // supply -- so the path goes plant -> supply -> community, matching PlantRepository's
    // p.supply.community.id. Note this is the *plant's* community, not e.supply.community: the
    // latter is the consuming supply's, which answers a different question.
    // Pending rows (valid_from IS NULL) are excluded; MIN would skip them anyway, and saying so
    // keeps the intent readable rather than relying on SQL's NULL handling.
    @Query("SELECT MIN(e.validFrom) FROM SupplyPartitionCoefficientEntity e " +
            "WHERE e.validFrom IS NOT NULL AND e.plant.supply.community.id = :communityId")
    Optional<Instant> findEarliestValidFromByCommunityId(@Param("communityId") UUID communityId);

    /**
     * Read-only existence check used by the sharing-agreement publish precondition. Phase 5c's
     * coefficient-materialization work should extend this repository rather than adding a
     * parallel one.
     */
    boolean existsBySharingAgreementId(UUID sharingAgreementId);

    /**
     * Read-only existence check used by the sharing-agreement revert-to-draft precondition: an
     * agreement can only go back to DRAFT while none of its coefficients have been applied by the
     * distributor yet.
     */
    boolean existsBySharingAgreementIdAndValidFromIsNotNull(UUID sharingAgreementId);

    // flushAutomatically: this bulk delete only touches supply_partition_coefficient's table
    // space, so Hibernate's auto-flush would not otherwise flush an unrelated pending entity (e.g.
    // a SharingAgreementFile insert earlier in the same transaction, as StoreDistributorFileServiceImpl
    // does) before running it -- and clearAutomatically then evicts that still-unflushed entity from
    // the persistence context, silently discarding it. Forcing the flush first avoids that.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM SupplyPartitionCoefficientEntity e WHERE e.sharingAgreement.id = :sharingAgreementId")
    void deleteBySharingAgreementId(@Param("sharingAgreementId") UUID sharingAgreementId);

    // -- Coefficient activation (phase 5f) --

    @Query("SELECT e FROM SupplyPartitionCoefficientEntity e WHERE e.id IN :ids AND e.sharingAgreement.id = :sharingAgreementId")
    List<SupplyPartitionCoefficientEntity> findAllByIdInAndSharingAgreementId(@Param("ids") List<UUID> ids,
                                                                                @Param("sharingAgreementId") UUID sharingAgreementId);

    List<SupplyPartitionCoefficientEntity> findBySharingAgreementId(UUID sharingAgreementId);

    // The coefficient for (plantId, supplyId) belonging to the nearest later non-DRAFT agreement of
    // the plant (by sharing_agreement.created_at ASC), regardless of whether that row is itself
    // activated -- unlike findActivatedAfterOrderByValidFromAsc, which only ever finds activated
    // rows. DRAFT agreements are always excluded: a draft-in-progress must never change the
    // displayed state of an existing, already-published row.
    @Query("SELECT e FROM SupplyPartitionCoefficientEntity e WHERE e.plant.id = :plantId AND e.supply.id = :supplyId " +
            "AND e.sharingAgreement.status <> :draftStatus AND e.sharingAgreement.createdAt > :afterCreatedAt " +
            "AND e.sharingAgreement.id <> :excludeAgreementId " +
            "ORDER BY e.sharingAgreement.createdAt ASC")
    List<SupplyPartitionCoefficientEntity> findNextCoefficientsForSupplyInLaterAgreements(
            @Param("plantId") UUID plantId,
            @Param("supplyId") UUID supplyId,
            @Param("draftStatus") SharingAgreementStatus draftStatus,
            @Param("afterCreatedAt") Instant afterCreatedAt,
            @Param("excludeAgreementId") UUID excludeAgreementId,
            Pageable pageable);

    // The currently open row for (plantId, supplyId), if any -- used when the coefficient being
    // activated has no validFrom of its own yet (pure activation).
    @Query("SELECT e FROM SupplyPartitionCoefficientEntity e WHERE e.plant.id = :plantId AND e.supply.id = :supplyId " +
            "AND e.id <> :excludeId AND e.validFrom IS NOT NULL AND e.validTo IS NULL")
    Optional<SupplyPartitionCoefficientEntity> findOpenPredecessor(@Param("plantId") UUID plantId,
                                                                     @Param("supplyId") UUID supplyId,
                                                                     @Param("excludeId") UUID excludeId);

    // The row whose validTo equals boundary -- used when correcting or reverting a coefficient that
    // already has its own validFrom set (the row may or may not still be the open one).
    @Query("SELECT e FROM SupplyPartitionCoefficientEntity e WHERE e.plant.id = :plantId AND e.supply.id = :supplyId " +
            "AND e.id <> :excludeId AND e.validFrom IS NOT NULL AND e.validTo = :boundary")
    Optional<SupplyPartitionCoefficientEntity> findPredecessorEndingAt(@Param("plantId") UUID plantId,
                                                                         @Param("supplyId") UUID supplyId,
                                                                         @Param("excludeId") UUID excludeId,
                                                                         @Param("boundary") Instant boundary);

    // The nearest activated row after afterInstant, however far away -- callers pass a single-item
    // Pageable (LIMIT 1) rather than loading every later row into memory.
    @Query("SELECT e FROM SupplyPartitionCoefficientEntity e WHERE e.plant.id = :plantId AND e.supply.id = :supplyId " +
            "AND e.id <> :excludeId AND e.validFrom IS NOT NULL AND e.validFrom > :afterInstant " +
            "ORDER BY e.validFrom ASC")
    List<SupplyPartitionCoefficientEntity> findActivatedAfterOrderByValidFromAsc(@Param("plantId") UUID plantId,
                                                                                   @Param("supplyId") UUID supplyId,
                                                                                   @Param("excludeId") UUID excludeId,
                                                                                   @Param("afterInstant") Instant afterInstant,
                                                                                   Pageable pageable);

    // Full history (any agreement, any time, pending or not) for plantId and any of supplyIds --
    // one query, used to project a batch's writes for overlap checking (phase 5f follow-up).
    @Query("SELECT e FROM SupplyPartitionCoefficientEntity e WHERE e.plant.id = :plantId AND e.supply.id IN :supplyIds")
    List<SupplyPartitionCoefficientEntity> findAllByPlantIdAndSupplyIdIn(@Param("plantId") UUID plantId,
                                                                           @Param("supplyIds") Collection<UUID> supplyIds);

    // -- Detail projections (multi-plant read API) --
    //
    // Constructor expressions selecting scalars only: no entity is materialised, so neither a lazy
    // proxy nor SupplyEntity's three eager @OneToOne(mappedBy) associations can fire. Each of these
    // is exactly one query regardless of how many rows come back.
    //
    // The plant filter is a separate method per query rather than a "(:plantId IS NULL OR ...)"
    // predicate: binding a null UUID parameter under Hibernate 6 + PostgreSQL fails with "could not
    // determine data type of parameter", and the adapter selects the method instead.

    String DETAIL_SELECT = "SELECT new org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient."
            + "SupplyPartitionCoefficientDetail("
            + "e.id, e.supply.id, e.supply.code, e.supply.name, "
            + "e.plant.id, e.plant.name, "
            + "e.sharingAgreement.id, e.sharingAgreement.name, e.sharingAgreement.status, "
            + "e.coefficient, e.validFrom, e.validTo, e.createdAt) "
            + "FROM SupplyPartitionCoefficientEntity e ";

    @Query(DETAIL_SELECT + "WHERE e.supply.id = :supplyId ORDER BY e.validFrom ASC")
    List<SupplyPartitionCoefficientDetail> findAllDetailsBySupplyId(@Param("supplyId") UUID supplyId);

    @Query(DETAIL_SELECT + "WHERE e.supply.id = :supplyId AND e.plant.id = :plantId ORDER BY e.validFrom ASC")
    List<SupplyPartitionCoefficientDetail> findAllDetailsBySupplyIdAndPlantId(@Param("supplyId") UUID supplyId,
                                                                             @Param("plantId") UUID plantId);

    // Active means activated and still open: validFrom IS NOT NULL excludes pending rows, which also
    // have a null validTo and would otherwise be indistinguishable from an active one.
    @Query(DETAIL_SELECT + "WHERE e.supply.id = :supplyId "
            + "AND e.validFrom IS NOT NULL AND e.validTo IS NULL ORDER BY e.plant.name ASC")
    List<SupplyPartitionCoefficientDetail> findActiveDetailsBySupplyId(@Param("supplyId") UUID supplyId);

    @Query(DETAIL_SELECT + "WHERE e.supply.id = :supplyId AND e.plant.id = :plantId "
            + "AND e.validFrom IS NOT NULL AND e.validTo IS NULL ORDER BY e.plant.name ASC")
    List<SupplyPartitionCoefficientDetail> findActiveDetailsBySupplyIdAndPlantId(@Param("supplyId") UUID supplyId,
                                                                                @Param("plantId") UUID plantId);

    // valid_from inclusive, valid_to exclusive; one row per plant covering the instant.
    @Query(DETAIL_SELECT + "WHERE e.supply.id = :supplyId AND e.validFrom IS NOT NULL "
            + "AND e.validFrom <= :timestamp AND (e.validTo IS NULL OR e.validTo > :timestamp) "
            + "ORDER BY e.plant.name ASC")
    List<SupplyPartitionCoefficientDetail> findDetailsBySupplyIdAtTimestamp(@Param("supplyId") UUID supplyId,
                                                                           @Param("timestamp") Instant timestamp);

    @Query(DETAIL_SELECT + "WHERE e.supply.id = :supplyId AND e.plant.id = :plantId AND e.validFrom IS NOT NULL "
            + "AND e.validFrom <= :timestamp AND (e.validTo IS NULL OR e.validTo > :timestamp) "
            + "ORDER BY e.plant.name ASC")
    List<SupplyPartitionCoefficientDetail> findDetailsBySupplyIdAndPlantIdAtTimestamp(@Param("supplyId") UUID supplyId,
                                                                                      @Param("plantId") UUID plantId,
                                                                                      @Param("timestamp") Instant timestamp);

    @Query(DETAIL_SELECT + "WHERE e.id IN :ids")
    List<SupplyPartitionCoefficientDetail> findAllDetailsByIdIn(@Param("ids") Collection<UUID> ids);

    // The active coefficient of each of supplyIds in one plant -- the batch behind a sharing
    // agreement's "current coefficient" column. Plant-scoped and validTo IS NULL, so the
    // no_overlapping_coefficients exclusion constraint guarantees at most one row per supply.
    @Query(DETAIL_SELECT + "WHERE e.plant.id = :plantId AND e.supply.id IN :supplyIds "
            + "AND e.validFrom IS NOT NULL AND e.validTo IS NULL")
    List<SupplyPartitionCoefficientDetail> findActiveDetailsByPlantIdAndSupplyIdIn(@Param("plantId") UUID plantId,
                                                                                  @Param("supplyIds") Collection<UUID> supplyIds);
}
