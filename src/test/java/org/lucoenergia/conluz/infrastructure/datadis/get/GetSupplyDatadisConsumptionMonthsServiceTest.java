package org.lucoenergia.conluz.infrastructure.datadis.get;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.get.SupplyDatadisConsumptionMonth;
import org.lucoenergia.conluz.domain.datadis.DatadisHourlyRecord;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The clock is at 2026-11-30T23:30Z: still November in UTC, already December in Madrid.
 */
@ExtendWith(MockitoExtension.class)
class GetSupplyDatadisConsumptionMonthsServiceTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final Instant NOW = Instant.parse("2026-11-30T23:30:00Z");

    @Mock
    private GetDatadisConsumptionRepository getDatadisConsumptionRepository;
    @Mock
    private GetSupplyRepository getSupplyRepository;
    @Mock
    private ZoneResolver zoneResolver;

    private GetSupplyDatadisConsumptionMonthsServiceImpl service;
    private final SupplyId supplyId = SupplyId.of(UUID.randomUUID());
    private final Supply supply = SupplyMother.random().withCode("ES0031406912345678JN0F").build();

    @BeforeEach
    void setUp() {
        service = new GetSupplyDatadisConsumptionMonthsServiceImpl(getDatadisConsumptionRepository,
                getSupplyRepository, zoneResolver, Clock.fixed(NOW, ZoneOffset.UTC));
        when(zoneResolver.resolveZoneIdForSupply(supplyId.getId())).thenReturn(MADRID);
    }

    @Test
    @DisplayName("DCX-006 the whole history is read in one query, up to the end of the current month in the supply's zone")
    void theWholeHistoryIsReadInOneQuery() {
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));
        when(getDatadisConsumptionRepository.getHourlyRecordsByPeriod(any(), any(), any())).thenReturn(List.of(
                record("2019-03-01T10:00:00Z"), record("2024-07-01T10:00:00Z"), record("2026-12-01T10:00:00Z")));

        assertEquals(3, service.getHourlyMonths(supplyId).size());

        verify(getDatadisConsumptionRepository).getHourlyRecordsByPeriod(supply,
                GetSupplyDatadisConsumptionMonthsServiceImpl.EARLIEST,
                OffsetDateTime.parse("2027-01-01T00:00+01:00"));
    }

    @Test
    @DisplayName("DCX-006 DCX-007 one entry per local month with records, newest first, counting its records against the whole month")
    void oneEntryPerLocalMonthNewestFirst() {
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));
        // 2026-03-31T22:00Z is already April in Madrid, 2026-10-31T23:00Z already November.
        when(getDatadisConsumptionRepository.getHourlyRecordsByPeriod(any(), any(), any())).thenReturn(List.of(
                record("2026-03-01T10:00:00Z"), record("2026-03-31T21:00:00Z"), record("2026-03-31T22:00:00Z"),
                record("2026-10-25T00:00:00Z"), record("2026-10-25T01:00:00Z"), record("2026-10-31T23:00:00Z")));

        assertEquals(List.of(
                        new SupplyDatadisConsumptionMonth(YearMonth.of(2026, 11), 1, 720),
                        new SupplyDatadisConsumptionMonth(YearMonth.of(2026, 10), 2, 745),
                        new SupplyDatadisConsumptionMonth(YearMonth.of(2026, 4), 1, 720),
                        new SupplyDatadisConsumptionMonth(YearMonth.of(2026, 3), 2, 743)),
                service.getHourlyMonths(supplyId));
    }

    @Test
    @DisplayName("DCX-006 a supply without records has no month")
    void noRecordsNoMonth() {
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));
        when(getDatadisConsumptionRepository.getHourlyRecordsByPeriod(any(), any(), any())).thenReturn(List.of());

        assertEquals(List.of(), service.getHourlyMonths(supplyId));
    }

    @Test
    @DisplayName("an unknown supply is not found, and nothing is read")
    void anUnknownSupplyIsNotFound() {
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class, () -> service.getHourlyMonths(supplyId));
        verifyNoInteractions(getDatadisConsumptionRepository);
    }

    private static DatadisHourlyRecord<DatadisConsumption> record(String instant) {
        return new DatadisHourlyRecord<>(Instant.parse(instant), new DatadisConsumption());
    }
}
