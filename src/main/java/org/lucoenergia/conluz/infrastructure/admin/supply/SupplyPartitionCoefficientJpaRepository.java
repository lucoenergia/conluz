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

    @Query("SELECT e FROM supply_partition_coefficient e WHERE e.supply.id = :supplyId AND e.validTo IS NULL")
    Optional<SupplyPartitionCoefficientEntity> findActiveBySupplyId(@Param("supplyId") UUID supplyId);

    // valid_from inclusive, valid_to exclusive
    @Query("SELECT e FROM supply_partition_coefficient e WHERE e.supply.id = :supplyId " +
            "AND e.validFrom <= :timestamp AND (e.validTo IS NULL OR e.validTo > :timestamp)")
    Optional<SupplyPartitionCoefficientEntity> findBySupplyIdAtTimestamp(
            @Param("supplyId") UUID supplyId,
            @Param("timestamp") Instant timestamp);

    // Periods overlapping [from, to): period starts before to AND (period is open OR ends after from)
    @Query("SELECT e FROM supply_partition_coefficient e WHERE e.supply.id = :supplyId " +
            "AND e.validFrom < :to AND (e.validTo IS NULL OR e.validTo > :from) " +
            "ORDER BY e.validFrom ASC")
    List<SupplyPartitionCoefficientEntity> findBySupplyIdInRange(
            @Param("supplyId") UUID supplyId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("SELECT e FROM supply_partition_coefficient e WHERE e.supply.id = :supplyId ORDER BY e.validFrom ASC")
    List<SupplyPartitionCoefficientEntity> findAllBySupplyIdOrderByValidFromAsc(@Param("supplyId") UUID supplyId);

    @Query("SELECT e FROM supply_partition_coefficient e WHERE e.validFrom <= :timestamp " +
            "AND (e.validTo IS NULL OR e.validTo > :timestamp)")
    List<SupplyPartitionCoefficientEntity> findAllActiveAtTimestamp(@Param("timestamp") Instant timestamp);

    @Modifying
    @Query("UPDATE supply_partition_coefficient e SET e.validTo = :validTo " +
            "WHERE e.supply.id = :supplyId AND e.validTo IS NULL")
    void closeActivePeriod(@Param("supplyId") UUID supplyId, @Param("validTo") Instant validTo);
}
