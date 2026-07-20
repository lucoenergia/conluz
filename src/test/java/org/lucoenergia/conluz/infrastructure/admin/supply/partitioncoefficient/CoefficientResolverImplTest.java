package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientResolver;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientSegment;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoefficientResolverImplTest {

    private static final UUID SUPPLY_ID = UUID.randomUUID();
    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final UUID OTHER_PLANT_ID = UUID.randomUUID();

    private SupplyPartitionCoefficientRepository repository;
    private CoefficientResolver resolver;

    @BeforeEach
    void setUp() {
        repository = mock(SupplyPartitionCoefficientRepository.class);
        resolver = new CoefficientResolverImpl(repository);
    }

    private SupplyPartitionCoefficient record(UUID plantId, BigDecimal coefficient, Instant validFrom, Instant validTo) {
        return new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(SUPPLY_ID)
                .withPlantId(plantId)
                .withSharingAgreementId(UUID.randomUUID())
                .withCoefficient(coefficient)
                .withValidFrom(validFrom)
                .withValidTo(validTo)
                .withCreatedAt(Instant.now())
                .build();
    }

    // --- resolveCoefficient (case 3 & 4) ---

    @Test
    void resolveCoefficientReturnsZeroWhenUncovered() {
        Instant instant = Instant.parse("2025-01-01T00:00:00Z");
        when(repository.findByPlantIdAndSupplyIdAtTimestamp(PLANT_ID, SUPPLY_ID, instant)).thenReturn(Optional.empty());

        BigDecimal result = resolver.resolveCoefficient(PLANT_ID, SUPPLY_ID, instant);

        assertEquals(0, BigDecimal.ZERO.compareTo(result));
    }

    @Test
    void resolveCoefficientReturnsZeroWhenPending() {
        // validFrom == null represents a pending, not-yet-scheduled coefficient. The
        // supply_partition_coefficient.valid_from column is NOT NULL today, so this row shape is
        // unreachable through a real DB read -- exercised here directly against a stubbed repository,
        // as documented on CoefficientResolver.
        Instant instant = Instant.parse("2025-01-01T00:00:00Z");
        SupplyPartitionCoefficient pending = record(PLANT_ID, BigDecimal.valueOf(0.5), null, null);
        when(repository.findByPlantIdAndSupplyIdAtTimestamp(PLANT_ID, SUPPLY_ID, instant)).thenReturn(Optional.of(pending));

        BigDecimal result = resolver.resolveCoefficient(PLANT_ID, SUPPLY_ID, instant);

        assertEquals(0, BigDecimal.ZERO.compareTo(result));
    }

    @Test
    void resolveCoefficientReturnsCoveringCoefficient() {
        Instant instant = Instant.parse("2025-01-01T00:00:00Z");
        SupplyPartitionCoefficient covering = record(PLANT_ID, BigDecimal.valueOf(0.75), Instant.parse("2024-01-01T00:00:00Z"), null);
        when(repository.findByPlantIdAndSupplyIdAtTimestamp(PLANT_ID, SUPPLY_ID, instant)).thenReturn(Optional.of(covering));

        BigDecimal result = resolver.resolveCoefficient(PLANT_ID, SUPPLY_ID, instant);

        assertEquals(0, BigDecimal.valueOf(0.75).compareTo(result));
    }

    // --- resolveCoefficientsBySupplyAtInstant ---

    @Test
    void resolveCoefficientsBySupplyAtInstantMapsEachPlantToItsCoefficient() {
        Instant instant = Instant.parse("2025-01-01T00:00:00Z");
        SupplyPartitionCoefficient onPlant1 = record(PLANT_ID, BigDecimal.valueOf(0.4), Instant.parse("2024-01-01T00:00:00Z"), null);
        SupplyPartitionCoefficient onPlant2 = record(OTHER_PLANT_ID, BigDecimal.valueOf(0.6), Instant.parse("2024-01-01T00:00:00Z"), null);
        when(repository.findAllBySupplyIdAtTimestamp(SUPPLY_ID, instant)).thenReturn(List.of(onPlant1, onPlant2));

        Map<UUID, BigDecimal> result = resolver.resolveCoefficientsBySupplyAtInstant(SUPPLY_ID, instant);

        assertEquals(2, result.size());
        assertEquals(0, BigDecimal.valueOf(0.4).compareTo(result.get(PLANT_ID)));
        assertEquals(0, BigDecimal.valueOf(0.6).compareTo(result.get(OTHER_PLANT_ID)));
    }

    @Test
    void resolveCoefficientsBySupplyAtInstantExcludesPendingRows() {
        Instant instant = Instant.parse("2025-01-01T00:00:00Z");
        SupplyPartitionCoefficient pending = record(PLANT_ID, BigDecimal.valueOf(0.4), null, null);
        when(repository.findAllBySupplyIdAtTimestamp(SUPPLY_ID, instant)).thenReturn(List.of(pending));

        Map<UUID, BigDecimal> result = resolver.resolveCoefficientsBySupplyAtInstant(SUPPLY_ID, instant);

        assertTrue(result.isEmpty());
    }

    @Test
    void resolveCoefficientsBySupplyAtInstantReturnsEmptyMapWhenNoCoverage() {
        Instant instant = Instant.parse("2025-01-01T00:00:00Z");
        when(repository.findAllBySupplyIdAtTimestamp(SUPPLY_ID, instant)).thenReturn(List.of());

        Map<UUID, BigDecimal> result = resolver.resolveCoefficientsBySupplyAtInstant(SUPPLY_ID, instant);

        assertTrue(result.isEmpty());
    }

    // --- resolveSegmentsBySupply ---

    @Test
    void resolveSegmentsBySupplyGroupsByPlant() {
        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-01-01T00:00:00Z");
        SupplyPartitionCoefficient onPlant1 = record(PLANT_ID, BigDecimal.valueOf(0.4), from, null);
        SupplyPartitionCoefficient onPlant2 = record(OTHER_PLANT_ID, BigDecimal.valueOf(0.6), from, null);
        when(repository.findBySupplyIdInRange(SUPPLY_ID, from, to)).thenReturn(List.of(onPlant1, onPlant2));

        Map<UUID, List<CoefficientSegment>> result = resolver.resolveSegmentsBySupply(SUPPLY_ID, from, to);

        assertEquals(2, result.size());
        assertEquals(1, result.get(PLANT_ID).size());
        assertEquals(1, result.get(OTHER_PLANT_ID).size());
    }

    @Test
    void resolveSegmentsBySupplyClampsToRequestedRange() {
        Instant periodStart = Instant.parse("2023-06-01T00:00:00Z");
        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-01-01T00:00:00Z");
        // Open-ended row starting well before `from` -- must clamp to [from, to).
        SupplyPartitionCoefficient openEnded = record(PLANT_ID, BigDecimal.valueOf(1.0), periodStart, null);
        when(repository.findBySupplyIdInRange(SUPPLY_ID, from, to)).thenReturn(List.of(openEnded));

        Map<UUID, List<CoefficientSegment>> result = resolver.resolveSegmentsBySupply(SUPPLY_ID, from, to);

        List<CoefficientSegment> segments = result.get(PLANT_ID);
        assertEquals(1, segments.size());
        assertEquals(from, segments.get(0).from());
        assertEquals(to, segments.get(0).to());
    }

    @Test
    void resolveSegmentsBySupplyOrdersSegmentsByFrom() {
        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant transition = Instant.parse("2024-06-15T00:00:00Z");
        Instant to = Instant.parse("2025-01-01T00:00:00Z");
        SupplyPartitionCoefficient before = record(PLANT_ID, BigDecimal.valueOf(0.3), from, transition);
        SupplyPartitionCoefficient after = record(PLANT_ID, BigDecimal.valueOf(0.7), transition, null);
        // Repository contract already orders by validFrom ASC -- assert the resolver preserves it.
        when(repository.findBySupplyIdInRange(SUPPLY_ID, from, to)).thenReturn(List.of(before, after));

        List<CoefficientSegment> segments = resolver.resolveSegmentsBySupply(SUPPLY_ID, from, to).get(PLANT_ID);

        assertEquals(2, segments.size());
        assertEquals(0, BigDecimal.valueOf(0.3).compareTo(segments.get(0).coefficient()));
        assertEquals(transition, segments.get(0).to());
        assertEquals(transition, segments.get(1).from());
        assertEquals(0, BigDecimal.valueOf(0.7).compareTo(segments.get(1).coefficient()));
    }

    @Test
    void resolveSegmentsBySupplyExcludesPendingRows() {
        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-01-01T00:00:00Z");
        SupplyPartitionCoefficient pending = record(PLANT_ID, BigDecimal.valueOf(0.5), null, null);
        when(repository.findBySupplyIdInRange(SUPPLY_ID, from, to)).thenReturn(List.of(pending));

        Map<UUID, List<CoefficientSegment>> result = resolver.resolveSegmentsBySupply(SUPPLY_ID, from, to);

        assertTrue(result.isEmpty());
    }

    @Test
    void resolveSegmentsBySupplyReturnsEmptyMapWhenNoRowsInRange() {
        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-01-01T00:00:00Z");
        when(repository.findBySupplyIdInRange(SUPPLY_ID, from, to)).thenReturn(List.of());

        Map<UUID, List<CoefficientSegment>> result = resolver.resolveSegmentsBySupply(SUPPLY_ID, from, to);

        assertTrue(result.isEmpty());
    }

    @Test
    void resolveSegmentsBySupplySkipsRowThatClampsToAnEmptyInterval() {
        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-01-01T00:00:00Z");
        // Row entirely before `from` would never be returned by a correct findBySupplyIdInRange
        // implementation, but the resolver still defends against a degenerate clamp (fromClamped >= toClamped).
        SupplyPartitionCoefficient degenerate = record(PLANT_ID, BigDecimal.valueOf(0.5), from, from);
        when(repository.findBySupplyIdInRange(SUPPLY_ID, from, to)).thenReturn(List.of(degenerate));

        Map<UUID, List<CoefficientSegment>> result = resolver.resolveSegmentsBySupply(SUPPLY_ID, from, to);

        assertFalse(result.containsKey(PLANT_ID));
    }
}
