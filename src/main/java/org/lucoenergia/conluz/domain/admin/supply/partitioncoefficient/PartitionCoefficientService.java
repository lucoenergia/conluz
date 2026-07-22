package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartitionCoefficientService {

    /**
     * Returns the coefficient active at the given instant.
     * Boundary rule: valid_from inclusive, valid_to exclusive.
     *
     * @throws SupplyPartitionCoefficientNotFoundException if no coefficient covers the given timestamp
     */
    BigDecimal findCoefficientByInstant(UUID supplyId, Instant timestamp);

    /**
     * Returns all coefficient periods overlapping [from, to), with valid_from and valid_to
     * clipped to the query range, ordered by valid_from ascending.
     */
    List<SupplyPartitionCoefficient> findAllCoefficientsInRange(UUID supplyId, Instant from, Instant to);

    /**
     * Returns the full history for the given supply ordered by valid_from ascending.
     */
    List<SupplyPartitionCoefficient> findAllCoefficientHistory(UUID supplyId);

    /**
     * Returns the active coefficient for the given supply.
     */
    Optional<SupplyPartitionCoefficient> findActiveBySupplyId(UUID supplyId);
}
