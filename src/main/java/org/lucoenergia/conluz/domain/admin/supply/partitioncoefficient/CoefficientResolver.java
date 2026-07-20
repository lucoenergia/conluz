package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves the partition coefficient (β) that applies to a supply's share of a plant's production,
 * at a given instant or over a range, from the {@code supply_partition_coefficient} timeline.
 * <p>
 * Two "β at instant t" contracts intentionally coexist in this codebase: {@link PartitionCoefficientService#findCoefficientByInstant}
 * throws when no coefficient covers the instant and is not plant-scoped (it answers an admin/CRUD
 * question: "what is the current registered coefficient for this supply"). This resolver instead
 * returns {@link BigDecimal#ZERO} for an uncovered instant, is plant-scoped, and never throws -- it
 * answers a production-calculation question: "how much of this plant's production, at this instant,
 * belongs to this supply." Do not merge the two.
 */
public interface CoefficientResolver {

    /**
     * β for {@code (plantId, supplyId)} active at {@code instant}. Returns {@link BigDecimal#ZERO} --
     * never throws -- when no segment covers the instant, or when the only matching row is pending
     * ({@code validFrom IS NULL}). This is the primitive matching this phase's {@code (plant, supply,
     * instant)} contract; it is not on any production-service hot path today (those use the batch
     * methods below to avoid one query per point/bucket), but is kept as the explicit single-point
     * contract this phase's tests exercise, and the shape a future self-consumption ({@code min()})
     * consumer will want.
     */
    BigDecimal resolveCoefficient(UUID plantId, UUID supplyId, Instant instant);

    /**
     * All plants with active coverage for {@code supplyId} at a single {@code instant}, mapped to
     * their β. Backs the instant-production path -- no synthetic range/epsilon window needed.
     */
    Map<UUID, BigDecimal> resolveCoefficientsBySupplyAtInstant(UUID supplyId, Instant instant);

    /**
     * All plants' coefficient segments overlapping {@code [from, to)} for {@code supplyId}, each
     * clamped to {@code [from, to)}, pending rows ({@code validFrom IS NULL}) excluded, ordered by
     * {@code from}. One repository round-trip regardless of plant count.
     * <p>
     * Callers MUST pass an already-exclusive {@code to} -- this method does not itself adjust for an
     * inclusive API boundary. The public production API's {@code endDate} is inclusive; converting
     * that into this method's half-open {@code to} is done once, at the top of each per-supply
     * service method (see {@code GetProductionServiceImpl}), not re-derived here.
     */
    Map<UUID, List<CoefficientSegment>> resolveSegmentsBySupply(UUID supplyId, Instant from, Instant to);
}
