package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PartitionCoefficientServiceTest {

    private PartitionCoefficientService service;
    private SupplyPartitionCoefficientRepository repository;
    private SupplyRepository supplyRepository;

    private static final UUID SUPPLY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(SupplyPartitionCoefficientRepository.class);
        supplyRepository = mock(SupplyRepository.class);
        service = new PartitionCoefficientServiceImpl(repository, supplyRepository);
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

    // --- registerCoefficientChange ---

    @Test
    void registerCoefficientChangeClosesActivePeriodAndCreatesNewOne() {
        Instant effectiveAt = Instant.parse("2025-06-01T00:00:00Z");
        BigDecimal newCoefficient = BigDecimal.valueOf(7.000000);

        when(supplyRepository.findById(SUPPLY_ID)).thenReturn(Optional.of(mock(
                org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity.class)));

        SupplyPartitionCoefficient saved = buildRecord(effectiveAt, null, newCoefficient);
        when(repository.save(any())).thenReturn(saved);

        SupplyPartitionCoefficient result = service.registerCoefficientChange(SUPPLY_ID, newCoefficient, effectiveAt);

        verify(repository).closeActivePeriod(SUPPLY_ID, effectiveAt);
        verify(repository).save(argThat(c ->
                c.getSupplyId().equals(SUPPLY_ID)
                        && c.getCoefficient().equals(newCoefficient)
                        && c.getValidFrom().equals(effectiveAt)
                        && c.getValidTo() == null));
        assertNotNull(result);
    }

    @Test
    void registerCoefficientChangeThrowsWhenSupplyNotFound() {
        when(supplyRepository.findById(SUPPLY_ID)).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class,
                () -> service.registerCoefficientChange(SUPPLY_ID, BigDecimal.ONE, Instant.now()));

        verifyNoInteractions(repository);
    }

    // --- computeCommunitySum ---

    @Test
    void computeCommunitySumReturnsCorrectTotal() {
        Instant timestamp = Instant.now();
        SupplyPartitionCoefficient c1 = buildRecord(timestamp.minusSeconds(1), null, BigDecimal.valueOf(30.000000));
        SupplyPartitionCoefficient c2 = buildRecord(timestamp.minusSeconds(1), null, BigDecimal.valueOf(70.000000));
        when(repository.findAllActiveAtTimestamp(timestamp)).thenReturn(List.of(c1, c2));

        BigDecimal sum = service.computeCommunitySum(timestamp);

        assertEquals(0, BigDecimal.valueOf(100.000000).compareTo(sum));
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
