package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplyPartitionCoefficientJpaRepository extends JpaRepository<SupplyPartitionCoefficientEntity, UUID> {

    @Query("SELECT e FROM SupplyPartitionCoefficientEntity e WHERE e.supply.id = :supplyId AND e.validTo IS NULL")
    Optional<SupplyPartitionCoefficientEntity> findActiveBySupplyId(@Param("supplyId") UUID supplyId);

    // valid_from inclusive, valid_to exclusive
    @Query("SELECT e FROM SupplyPartitionCoefficientEntity e WHERE e.supply.id = :supplyId " +
            "AND e.validFrom <= :timestamp AND (e.validTo IS NULL OR e.validTo > :timestamp)")
    Optional<SupplyPartitionCoefficientEntity> findBySupplyIdAtTimestamp(
            @Param("supplyId") UUID supplyId,
            @Param("timestamp") Instant timestamp);

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

    /**
     * Read-only existence check used by the sharing-agreement publish precondition. Phase 5c's
     * coefficient-materialization work should extend this repository rather than adding a
     * parallel one.
     */
    boolean existsBySharingAgreementId(UUID sharingAgreementId);

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
}
