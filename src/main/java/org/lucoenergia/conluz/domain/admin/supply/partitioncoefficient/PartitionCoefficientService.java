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
    BigDecimal findCoefficientByInstant(UUID supplyId, Instant timestamp);

    /**
     * Returns all coefficient periods overlapping [from, to), with valid_from and valid_to
     * clipped to the query range, ordered by valid_from ascending.
     */
    List<SupplyPartitionCoefficient> findAllCoefficientsInRange(UUID supplyId, Instant from, Instant to);

    /**
     * The full history for the given supply, ordered by valid_from ascending, enriched with the
     * supply, plant and agreement each period refers to. Pending periods are included.
     */
    List<SupplyPartitionCoefficientDetail> findAllCoefficientHistory(UUID supplyId);

    /**
     * The active coefficient of the supply in each plant it participates in -- at most one per
     * plant, and empty when the supply has none.
     *
     * <p>Active means {@code validFrom != null && validTo == null}. A supply may legitimately be
     * active in several plants at once, so this is a list: the
     * {@code no_overlapping_coefficients} exclusion constraint is scoped to (plant, supply), not to
     * the supply alone.
     */
    List<SupplyPartitionCoefficientDetail> findActiveBySupplyId(UUID supplyId);

    /**
     * Details for the given coefficients, in the same order they were given. Enriches the result of
     * a write in one query; the order is restored explicitly because SQL {@code IN} does not
     * preserve it.
     */
    List<SupplyPartitionCoefficientDetail> findDetailsInOrderOf(List<SupplyPartitionCoefficient> coefficients);
}
