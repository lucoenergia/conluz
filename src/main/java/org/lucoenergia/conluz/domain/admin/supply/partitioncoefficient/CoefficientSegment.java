package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A coefficient active over the half-open interval {@code [from, to)}, matching the
 * {@code supply_partition_coefficient} table's {@code tstzrange(valid_from, valid_to, '[)')} semantics.
 * Produced by {@link CoefficientResolver#resolveSegmentsBySupply} already clamped to the caller's
 * requested range.
 */
public record CoefficientSegment(Instant from, Instant to, BigDecimal coefficient) {
}
