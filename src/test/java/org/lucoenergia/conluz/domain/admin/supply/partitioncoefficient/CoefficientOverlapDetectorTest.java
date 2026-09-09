package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoefficientOverlapDetectorTest {

    private static final UUID PLANT_ID = UUID.randomUUID();

    private SupplyPartitionCoefficient coefficient(UUID supplyId, Instant validFrom, Instant validTo) {
        return new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supplyId)
                .withPlantId(PLANT_ID)
                .withSharingAgreementId(UUID.randomUUID())
                .withCoefficient(BigDecimal.ONE)
                .withValidFrom(validFrom)
                .withValidTo(validTo)
                .withCreatedAt(Instant.now())
                .build();
    }

    @Test
    void onlyComparesRangesWithinTheSameSupplyNotAcrossSupplies() {
        UUID supplyA = UUID.randomUUID();
        UUID supplyB = UUID.randomUUID();
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2024-06-01T00:00:00Z");
        Instant t3 = Instant.parse("2025-06-01T00:00:00Z");

        // Each supply has one genuinely overlapping pair, and both supplies use the identical
        // windows -- a single-supply fixture can't prove grouping does anything; if grouping were
        // broken (comparing every pair regardless of supply), this would report far more than 2.
        List<SupplyPartitionCoefficient> projected = List.of(
                coefficient(supplyA, t0, t1),
                coefficient(supplyA, t2, t3),
                coefficient(supplyB, t0, t1),
                coefficient(supplyB, t2, t3)
        );

        assertEquals(2, CoefficientOverlapDetector.findOverlaps(projected).size());
    }

    @Test
    void predecessorClosedByTheCascadeAndItsSuccessorComeOutClean() {
        UUID supplyId = UUID.randomUUID();
        Instant predecessorValidFrom = Instant.parse("2024-05-23T00:00:00Z");
        Instant activationDate = Instant.parse("2026-09-08T00:00:00Z");

        // This is the exact shape of the reported bug: an open predecessor projected to be closed
        // by the cascade, and the successor projected to open exactly where it closes.
        SupplyPartitionCoefficient predecessorProjected = coefficient(supplyId, predecessorValidFrom, activationDate);
        SupplyPartitionCoefficient successorProjected = coefficient(supplyId, activationDate, null);

        assertTrue(CoefficientOverlapDetector.findOverlaps(List.of(predecessorProjected, successorProjected)).isEmpty());
    }

    @Test
    void reportsAGenuineOverlapForTheSameSupply() {
        UUID supplyId = UUID.randomUUID();
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2024-06-01T00:00:00Z");
        Instant t3 = Instant.parse("2025-06-01T00:00:00Z");

        List<CoefficientOverlapDetector.Overlap> overlaps = CoefficientOverlapDetector.findOverlaps(List.of(
                coefficient(supplyId, t0, t1),
                coefficient(supplyId, t2, t3)
        ));

        assertEquals(1, overlaps.size());
    }

    @Test
    void adjacentBoundariesAreNotAnOverlap() {
        UUID supplyId = UUID.randomUUID();
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-01-01T00:00:00Z");

        List<CoefficientOverlapDetector.Overlap> overlaps = CoefficientOverlapDetector.findOverlaps(List.of(
                coefficient(supplyId, t0, t1),
                coefficient(supplyId, t1, t2)
        ));

        assertTrue(overlaps.isEmpty());
    }

    @Test
    void aPendingRowNeverParticipatesRegardlessOfItsValidTo() {
        UUID supplyId = UUID.randomUUID();
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");

        List<CoefficientOverlapDetector.Overlap> overlaps = CoefficientOverlapDetector.findOverlaps(List.of(
                coefficient(supplyId, t0, null),
                coefficient(supplyId, null, null)
        ));

        assertTrue(overlaps.isEmpty());
    }
}
