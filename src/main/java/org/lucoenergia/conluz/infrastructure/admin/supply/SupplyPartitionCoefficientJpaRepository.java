package org.lucoenergia.conluz.infrastructure.admin.supply;

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

    @Query("SELECT e FROM SupplyPartitionCoefficientEntity e WHERE e.supply.id = :supplyId " +
            "AND e.plant.id = :plantId AND e.validTo IS NULL")
    Optional<SupplyPartitionCoefficientEntity> findActiveBySupplyIdAndPlantId(
            @Param("supplyId") UUID supplyId,
            @Param("plantId") UUID plantId);

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

    @Modifying(clearAutomatically = true)
    @Query("UPDATE SupplyPartitionCoefficientEntity e SET e.validTo = :validTo " +
            "WHERE e.supply.id = :supplyId AND e.plant.id = :plantId AND e.validTo IS NULL")
    void closeActivePeriod(@Param("supplyId") UUID supplyId, @Param("plantId") UUID plantId, @Param("validTo") Instant validTo);

    /**
     * Read-only existence check used by the sharing-agreement publish precondition. Phase 5c's
     * coefficient-materialization work should extend this repository rather than adding a
     * parallel one.
     */
    boolean existsBySharingAgreementId(UUID sharingAgreementId);
}
