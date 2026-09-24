package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient.PartitionCoefficientServiceImpl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PartitionCoefficientServiceTest {

    private PartitionCoefficientService service;
    private GetSupplyPartitionCoefficientRepository repository;

    private static final UUID SUPPLY_ID = UUID.randomUUID();
    private static final UUID PLANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(GetSupplyPartitionCoefficientRepository.class);
        service = new PartitionCoefficientServiceImpl(repository);
    }

    // --- findCoefficientsByInstant ---

    @Test
    void findCoefficientsByInstantAtMidnightTimestamp() {
        Instant midnight = LocalDate.of(2025, 1, 15).atStartOfDay().toInstant(ZoneOffset.UTC);
        SupplyPartitionCoefficientDetail record = SupplyPartitionCoefficientDetailMother.of(
                buildRecord(midnight.minusSeconds(3600), null, BigDecimal.valueOf(3.076300)));
        when(repository.findDetailsBySupplyIdAtTimestamp(SUPPLY_ID, null, midnight)).thenReturn(List.of(record));

        List<SupplyPartitionCoefficientDetail> result = service.findCoefficientsByInstant(SUPPLY_ID, null, midnight);

        assertEquals(1, result.size());
        assertEquals(BigDecimal.valueOf(3.076300), result.get(0).getCoefficientValue());
    }

    @Test
    void findCoefficientsByInstantAtMidHourTimestamp() {
        Instant midHour = Instant.parse("2025-03-10T14:30:00Z");
        SupplyPartitionCoefficientDetail record = SupplyPartitionCoefficientDetailMother.of(
                buildRecord(Instant.parse("2025-01-01T00:00:00Z"), null, BigDecimal.valueOf(2.543200)));
        when(repository.findDetailsBySupplyIdAtTimestamp(SUPPLY_ID, null, midHour)).thenReturn(List.of(record));

        List<SupplyPartitionCoefficientDetail> result = service.findCoefficientsByInstant(SUPPLY_ID, null, midHour);

        assertEquals(1, result.size());
        assertEquals(BigDecimal.valueOf(2.543200), result.get(0).getCoefficientValue());
    }

    @Test
    void findCoefficientsByInstantReturnsOneEntryPerPlantCoveringTheInstant() {
        Instant instant = Instant.parse("2025-06-01T12:00:00Z");
        SupplyPartitionCoefficientDetail inX = SupplyPartitionCoefficientDetailMother.random(
                BigDecimal.valueOf(0.4), instant.minusSeconds(3600), null);
        SupplyPartitionCoefficientDetail inY = SupplyPartitionCoefficientDetailMother.random(
                BigDecimal.valueOf(0.6), instant.minusSeconds(3600), null);
        when(repository.findDetailsBySupplyIdAtTimestamp(SUPPLY_ID, null, instant)).thenReturn(List.of(inX, inY));

        assertEquals(2, service.findCoefficientsByInstant(SUPPLY_ID, null, instant).size());
        // A null plant means "every plant the supply participates in".
        verify(repository).findDetailsBySupplyIdAtTimestamp(SUPPLY_ID, null, instant);
    }

    @Test
    void findCoefficientsByInstantReturnsEmptyListWhenNoPeriodCoversTheInstant() {
        Instant timestamp = Instant.parse("2020-01-01T00:00:00Z");
        when(repository.findDetailsBySupplyIdAtTimestamp(SUPPLY_ID, null, timestamp)).thenReturn(List.of());

        // An empty collection, not a 404: nothing covering an instant is a normal answer.
        assertTrue(service.findCoefficientsByInstant(SUPPLY_ID, null, timestamp).isEmpty());
    }

    // --- resolveCoefficientsInRange ---

    @Test
    void findAllCoefficientsInRangeWithOneChange_returnsTwoTuples() {
        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant changeAt = Instant.parse("2025-03-01T00:00:00Z");
        Instant to = Instant.parse("2025-06-01T00:00:00Z");

        SupplyPartitionCoefficient period1 = buildRecord(from.minusSeconds(1), changeAt, BigDecimal.valueOf(3.000000));
        SupplyPartitionCoefficient period2 = buildRecord(changeAt, null, BigDecimal.valueOf(4.000000));
        when(repository.findBySupplyIdInRange(SUPPLY_ID, from, to)).thenReturn(List.of(period1, period2));

        List<SupplyPartitionCoefficient> result = service.findAllCoefficientsInRange(SUPPLY_ID, from, to);

        assertEquals(2, result.size());
        // First tuple clipped: validFrom = from (query start)
        assertEquals(from, result.get(0).getValidFrom());
        assertEquals(changeAt, result.get(0).getValidTo());
        // Second tuple clipped: validTo = to (query end)
        assertEquals(changeAt, result.get(1).getValidFrom());
        assertEquals(to, result.get(1).getValidTo());
    }

    @Test
    void findAllCoefficientsInRangeWithMultipleChanges_returnsNTuples() {
        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2025-02-01T00:00:00Z");
        Instant t2 = Instant.parse("2025-04-01T00:00:00Z");
        Instant to = Instant.parse("2025-07-01T00:00:00Z");

        SupplyPartitionCoefficient p1 = buildRecord(from.minusSeconds(1), t1, BigDecimal.valueOf(1.000000));
        SupplyPartitionCoefficient p2 = buildRecord(t1, t2, BigDecimal.valueOf(2.000000));
        SupplyPartitionCoefficient p3 = buildRecord(t2, null, BigDecimal.valueOf(3.000000));
        when(repository.findBySupplyIdInRange(SUPPLY_ID, from, to)).thenReturn(List.of(p1, p2, p3));

        List<SupplyPartitionCoefficient> result = service.findAllCoefficientsInRange(SUPPLY_ID, from, to);

        assertEquals(3, result.size());
        assertEquals(from, result.get(0).getValidFrom());
        assertEquals(t1, result.get(0).getValidTo());
        assertEquals(t1, result.get(1).getValidFrom());
        assertEquals(t2, result.get(1).getValidTo());
        assertEquals(t2, result.get(2).getValidFrom());
        assertEquals(to, result.get(2).getValidTo()); // open period clipped to query end
    }

    @Test
    void findAllCoefficientsInRangeFullyWithinOnePeriod_returnsOneTupleWithBoundsClipped() {
        Instant periodStart = Instant.parse("2025-01-01T00:00:00Z");
        Instant from = Instant.parse("2025-03-01T00:00:00Z");
        Instant to = Instant.parse("2025-05-01T00:00:00Z");

        SupplyPartitionCoefficient period = buildRecord(periodStart, null, BigDecimal.valueOf(5.000000));
        when(repository.findBySupplyIdInRange(SUPPLY_ID, from, to)).thenReturn(List.of(period));

        List<SupplyPartitionCoefficient> result = service.findAllCoefficientsInRange(SUPPLY_ID, from, to);

        assertEquals(1, result.size());
        assertEquals(from, result.get(0).getValidFrom());  // clipped to query start
        assertEquals(to, result.get(0).getValidTo());       // clipped to query end
        assertEquals(BigDecimal.valueOf(5.000000), result.get(0).getCoefficient());
    }

    // --- findAllCoefficientHistory ---

    @Test
    void findAllCoefficientHistory_delegatesAcrossEveryPlantAndReturnsOrderedList() {
        Instant t0 = Instant.parse("2023-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2024-01-01T00:00:00Z");
        SupplyPartitionCoefficientDetail p1 =
                SupplyPartitionCoefficientDetailMother.of(buildRecord(t0, t1, BigDecimal.valueOf(1.0)));
        SupplyPartitionCoefficientDetail p2 =
                SupplyPartitionCoefficientDetailMother.of(buildRecord(t1, null, BigDecimal.valueOf(2.0)));
        when(repository.findAllDetailsBySupplyId(SUPPLY_ID, null, true)).thenReturn(List.of(p1, p2));

        List<SupplyPartitionCoefficientDetail> result = service.findAllCoefficientHistory(SUPPLY_ID, null, true);

        assertEquals(2, result.size());
        assertEquals(t0, result.get(0).getValidFrom());
        assertEquals(t1, result.get(1).getValidFrom());
        // A null plant means "every plant the supply participates in".
        verify(repository).findAllDetailsBySupplyId(SUPPLY_ID, null, true);
    }

    /**
     * The service decides nothing about pending rows -- it carries the caller's decision to the
     * query. A service that quietly forced the flag would make the controller's authorization-derived
     * choice ineffective.
     */
    @Test
    void findAllCoefficientHistory_carriesIncludePendingThroughToTheQuery() {
        SupplyPartitionCoefficientDetail activated = SupplyPartitionCoefficientDetailMother.of(
                buildRecord(Instant.parse("2023-01-01T00:00:00Z"), null, BigDecimal.valueOf(1.0)));
        when(repository.findAllDetailsBySupplyId(SUPPLY_ID, PLANT_ID, false)).thenReturn(List.of(activated));

        List<SupplyPartitionCoefficientDetail> result =
                service.findAllCoefficientHistory(SUPPLY_ID, PLANT_ID, false);

        assertEquals(1, result.size());
        verify(repository).findAllDetailsBySupplyId(SUPPLY_ID, PLANT_ID, false);
    }

    // --- findActiveBySupplyId ---

    @Test
    void findActiveBySupplyId_returnsOneEntryPerPlantTheSupplyIsActiveIn() {
        SupplyPartitionCoefficientDetail inX = SupplyPartitionCoefficientDetailMother.random(
                BigDecimal.valueOf(0.4), Instant.now().minusSeconds(3600), null);
        SupplyPartitionCoefficientDetail inY = SupplyPartitionCoefficientDetailMother.random(
                BigDecimal.valueOf(0.6), Instant.now().minusSeconds(3600), null);
        when(repository.findActiveDetailsBySupplyId(SUPPLY_ID, null)).thenReturn(List.of(inX, inY));

        List<SupplyPartitionCoefficientDetail> result = service.findActiveBySupplyId(SUPPLY_ID, null);

        assertEquals(2, result.size());
        verify(repository).findActiveDetailsBySupplyId(SUPPLY_ID, null);
    }

    @Test
    void findActiveBySupplyId_returnsEmptyList_whenNoActiveRecord() {
        when(repository.findActiveDetailsBySupplyId(SUPPLY_ID, null)).thenReturn(List.of());

        assertTrue(service.findActiveBySupplyId(SUPPLY_ID, null).isEmpty());
    }

    // --- helpers ---

    private SupplyPartitionCoefficient buildRecord(Instant validFrom, Instant validTo, BigDecimal coefficient) {
        return new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(SUPPLY_ID)
                .withCoefficient(coefficient)
                .withValidFrom(validFrom)
                .withValidTo(validTo)
                .withCreatedAt(Instant.now())
                .build();
    }
}
