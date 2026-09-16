package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PartitionCoefficientService {

    /**
     * One entry per plant whose coefficient for the supply covers {@code timestamp}, with
     * valid_from inclusive and valid_to exclusive. Empty when no period covers the instant.
     *
     * <p>A supply may hold a coefficient in several plants at the same instant, so this is a list.
     * Pending periods are excluded: a coefficient the distributor never applied covered no instant.
     *
     * <p>A null {@code plantId} means every plant the supply participates in; a plant the supply has
     * no coefficient in yields an empty list rather than an error.
     */
    List<SupplyPartitionCoefficientDetail> findCoefficientsByInstant(UUID supplyId, UUID plantId, Instant timestamp);

    /**
     * Returns all coefficient periods overlapping [from, to), with valid_from and valid_to
     * clipped to the query range, ordered by valid_from ascending.
     */
    List<SupplyPartitionCoefficient> findAllCoefficientsInRange(UUID supplyId, Instant from, Instant to);

    /**
     * The full history for the given supply, ordered by valid_from ascending, enriched with the
     * supply, plant and agreement each period refers to. Pending periods are included.
     *
     * <p>A null {@code plantId} means every plant the supply participates in; a plant the supply has
     * no coefficient in yields an empty list rather than an error.
     */
    List<SupplyPartitionCoefficientDetail> findAllCoefficientHistory(UUID supplyId, UUID plantId);

    /**
     * The active coefficient of the supply in each plant it participates in -- at most one per
     * plant, and empty when the supply has none.
     *
     * <p>Active means {@code validFrom != null && validTo == null}. A supply may legitimately be
     * active in several plants at once, so this is a list: the
     * {@code no_overlapping_coefficients} exclusion constraint is scoped to (plant, supply), not to
     * the supply alone.
     *
     * <p>A null {@code plantId} means every plant the supply participates in; a plant the supply has
     * no coefficient in yields an empty list rather than an error.
     */
    List<SupplyPartitionCoefficientDetail> findActiveBySupplyId(UUID supplyId, UUID plantId);

    /**
     * Details for the given coefficients, in the same order they were given. Enriches the result of
     * a write in one query; the order is restored explicitly because SQL {@code IN} does not
     * preserve it.
     */
    List<SupplyPartitionCoefficientDetail> findDetailsInOrderOf(List<SupplyPartitionCoefficient> coefficients);
}
