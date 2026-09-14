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

    @BeforeEach
    void setUp() {
        repository = mock(GetSupplyPartitionCoefficientRepository.class);
        service = new PartitionCoefficientServiceImpl(repository);
    }

    // --- resolveCoefficient ---

    @Test
    void findCoefficientByInstantAtMidnightTimestamp() {
        Instant midnight = LocalDate.of(2025, 1, 15).atStartOfDay().toInstant(ZoneOffset.UTC);
        SupplyPartitionCoefficient record = buildRecord(
                midnight.minusSeconds(3600), null, BigDecimal.valueOf(3.076300));
        when(repository.findBySupplyIdAtTimestamp(SUPPLY_ID, midnight)).thenReturn(Optional.of(record));

        BigDecimal result = service.findCoefficientByInstant(SUPPLY_ID, midnight);

        assertEquals(BigDecimal.valueOf(3.076300), result);
    }

    @Test
    void findCoefficientByInstantAtMidHourTimestamp() {
        Instant midHour = Instant.parse("2025-03-10T14:30:00Z");
        SupplyPartitionCoefficient record = buildRecord(
                Instant.parse("2025-01-01T00:00:00Z"), null, BigDecimal.valueOf(2.543200));
        when(repository.findBySupplyIdAtTimestamp(SUPPLY_ID, midHour)).thenReturn(Optional.of(record));

        BigDecimal result = service.findCoefficientByInstant(SUPPLY_ID, midHour);

        assertEquals(BigDecimal.valueOf(2.543200), result);
    }

    @Test
    void findCoefficientByInstant() {
        // Boundary rule: valid_from inclusive, valid_to exclusive.
        // A query at T2 (the exact change time) should return the NEW period (valid_from = T2).
        Instant changeAt = Instant.parse("2025-06-01T00:00:00Z");
        SupplyPartitionCoefficient newPeriod = buildRecord(changeAt, null, BigDecimal.valueOf(4.000000));
        when(repository.findBySupplyIdAtTimestamp(SUPPLY_ID, changeAt)).thenReturn(Optional.of(newPeriod));

        BigDecimal result = service.findCoefficientByInstant(SUPPLY_ID, changeAt);

        assertEquals(BigDecimal.valueOf(4.000000), result);
    }

    @Test
    void findCoefficientByInstantThrowsWhenNoHistoryExists() {
        Instant timestamp = Instant.parse("2020-01-01T00:00:00Z");
        when(repository.findBySupplyIdAtTimestamp(SUPPLY_ID, timestamp)).thenReturn(Optional.empty());

        assertThrows(SupplyPartitionCoefficientNotFoundException.class,
                () -> service.findCoefficientByInstant(SUPPLY_ID, timestamp));
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
    void findAllCoefficientHistory_delegatesAndReturnsOrderedList() {
        Instant t0 = Instant.parse("2023-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2024-01-01T00:00:00Z");
        SupplyPartitionCoefficient p1 = buildRecord(t0, t1, BigDecimal.valueOf(1.0));
        SupplyPartitionCoefficient p2 = buildRecord(t1, null, BigDecimal.valueOf(2.0));
        when(repository.findAllBySupplyIdOrderByValidFromAsc(SUPPLY_ID)).thenReturn(List.of(p1, p2));

        List<SupplyPartitionCoefficient> result = service.findAllCoefficientHistory(SUPPLY_ID);

        assertEquals(2, result.size());
        assertEquals(t0, result.get(0).getValidFrom());
        assertEquals(t1, result.get(1).getValidFrom());
        verify(repository).findAllBySupplyIdOrderByValidFromAsc(SUPPLY_ID);
    }

    // --- findActiveBySupplyId ---

    @Test
    void findActiveBySupplyId_delegatesAndReturnsOptional() {
        SupplyPartitionCoefficient active = buildRecord(Instant.now().minusSeconds(3600), null, BigDecimal.valueOf(5.0));
        when(repository.findActiveBySupplyId(SUPPLY_ID)).thenReturn(Optional.of(active));

        Optional<SupplyPartitionCoefficient> result = service.findActiveBySupplyId(SUPPLY_ID);

        assertTrue(result.isPresent());
        assertEquals(active, result.get());
    }

    @Test
    void findActiveBySupplyId_returnsEmpty_whenNoActiveRecord() {
        when(repository.findActiveBySupplyId(SUPPLY_ID)).thenReturn(Optional.empty());

        Optional<SupplyPartitionCoefficient> result = service.findActiveBySupplyId(SUPPLY_ID);

        assertTrue(result.isEmpty());
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
