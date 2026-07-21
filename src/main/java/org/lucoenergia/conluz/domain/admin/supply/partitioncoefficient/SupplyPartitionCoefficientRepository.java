package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplyPartitionCoefficientRepository {

    Optional<SupplyPartitionCoefficient> findActiveBySupplyId(UUID supplyId);

    Optional<SupplyPartitionCoefficient> findBySupplyIdAtTimestamp(UUID supplyId, Instant timestamp);

    /**
     * The coefficient for a specific {@code (plantId, supplyId)} pair active at {@code timestamp}.
     * Unlike {@link #findBySupplyIdAtTimestamp}, this is unambiguous even when the supply has
     * concurrently-active coefficients across multiple plants (permitted by the
     * {@code no_overlapping_coefficients} exclusion constraint, which is scoped to
     * {@code (plant_id, supply_id)}, not {@code supply_id} alone).
     */
    Optional<SupplyPartitionCoefficient> findByPlantIdAndSupplyIdAtTimestamp(UUID plantId, UUID supplyId, Instant timestamp);

    /**
     * Every coefficient for {@code supplyId} -- across all of its plants -- active at {@code timestamp}.
     */
    List<SupplyPartitionCoefficient> findAllBySupplyIdAtTimestamp(UUID supplyId, Instant timestamp);

    List<SupplyPartitionCoefficient> findBySupplyIdInRange(UUID supplyId, Instant from, Instant to);

    List<SupplyPartitionCoefficient> findAllBySupplyIdOrderByValidFromAsc(UUID supplyId);

    List<SupplyPartitionCoefficient> findAllActiveAtTimestamp(Instant timestamp);

    SupplyPartitionCoefficient save(SupplyPartitionCoefficient coefficient);

    void closeActivePeriod(UUID supplyId, UUID plantId, Instant validTo);

    void syncSupplyPartitionCoefficient(UUID supplyId, BigDecimal newCoefficient);

    /**
     * Read-only existence check used by the sharing-agreement publish precondition. Phase 5c's
     * coefficient-materialization work should extend this repository rather than adding a
     * parallel one.
     */
    boolean existsBySharingAgreementId(UUID sharingAgreementId);
}
