package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Pairwise range-overlap check over a projected set of coefficients, grouped by supplyId, using
 * the same half-open '[)' semantics as the no_overlapping_coefficients exclusion constraint (a
 * null validTo is +infinity; touching boundaries are adjacent, not overlapping). Pending rows
 * (validFrom == null) never participate, mirroring the constraint's own partial predicate.
 * Callers are responsible for projecting: passing each row's intended final state, not
 * necessarily what is currently persisted.
 */
public final class CoefficientOverlapDetector {

    private CoefficientOverlapDetector() {
    }

    public record Overlap(SupplyPartitionCoefficient first, SupplyPartitionCoefficient second) {
    }

    public static List<Overlap> findOverlaps(List<SupplyPartitionCoefficient> projected) {
        List<Overlap> overlaps = new ArrayList<>();
        Map<UUID, List<SupplyPartitionCoefficient>> bySupply = projected.stream()
                .filter(c -> c.getValidFrom() != null)
                .collect(Collectors.groupingBy(SupplyPartitionCoefficient::getSupplyId));
        for (List<SupplyPartitionCoefficient> group : bySupply.values()) {
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    if (rangesOverlap(group.get(i), group.get(j))) {
                        overlaps.add(new Overlap(group.get(i), group.get(j)));
                    }
                }
            }
        }
        return overlaps;
    }

    private static boolean rangesOverlap(SupplyPartitionCoefficient a, SupplyPartitionCoefficient b) {
        boolean aStartsBeforeBEnds = b.getValidTo() == null || a.getValidFrom().isBefore(b.getValidTo());
        boolean bStartsBeforeAEnds = a.getValidTo() == null || b.getValidFrom().isBefore(a.getValidTo());
        return aStartsBeforeBEnds && bStartsBeforeAEnds;
    }
}
