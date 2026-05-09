package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PartitionCoefficientService {

    /**
     * Returns the coefficient active at the given instant.
     * Boundary rule: valid_from inclusive, valid_to exclusive.
     *
     * @throws SupplyPartitionCoefficientNotFoundException if no coefficient covers the given timestamp
     */
    BigDecimal resolveCoefficient(UUID supplyId, Instant timestamp);

    /**
     * Returns all coefficient periods overlapping [from, to), with valid_from and valid_to
     * clipped to the query range, ordered by valid_from ascending.
     */
    List<SupplyPartitionCoefficient> resolveCoefficientsInRange(UUID supplyId, Instant from, Instant to);

    /**
     * Closes the currently active period by setting its valid_to = effectiveAt,
     * inserts a new active row with valid_from = effectiveAt, and updates
     * supply.partition_coefficient denormalization.
     */
    SupplyPartitionCoefficient registerCoefficientChange(UUID supplyId, BigDecimal newCoefficient, Instant effectiveAt);

    /**
     * Returns the full history for the given supply ordered by valid_from ascending.
     */
    List<SupplyPartitionCoefficient> getCoefficientHistory(UUID supplyId);

    /**
     * Computes the sum of active coefficients across all supplies at the given instant.
     * Used to issue a warning when the total differs from 100 by more than a small tolerance.
     * Never blocks writes.
     */
    BigDecimal computeCommunitySum(Instant timestamp);
}
