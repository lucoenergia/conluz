package org.lucoenergia.conluz.infrastructure.datadis.get;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.get.ConsumptionCsvMonthInFutureException;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.get.SupplyDatadisConsumptionCsv;
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
class GetSupplyDatadisConsumptionCsvServiceTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final Instant NOW = Instant.parse("2026-11-30T23:30:00Z");

    @Mock
    private GetDatadisConsumptionRepository getDatadisConsumptionRepository;
    @Mock
    private GetSupplyRepository getSupplyRepository;
    @Mock
    private ZoneResolver zoneResolver;

    private GetSupplyDatadisConsumptionCsvServiceImpl service;
    private final SupplyId supplyId = SupplyId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new GetSupplyDatadisConsumptionCsvServiceImpl(getDatadisConsumptionRepository, getSupplyRepository,
                zoneResolver, Clock.fixed(NOW, ZoneOffset.UTC));
        when(zoneResolver.resolveZoneIdForSupply(supplyId.getId())).thenReturn(MADRID);
    }

    @Test
    @DisplayName("DCX-003 a month after the current one in the application's time zone is refused before anything is read")
    void aFutureMonthIsRefused() {
        ConsumptionCsvMonthInFutureException refused = assertThrows(ConsumptionCsvMonthInFutureException.class,
                () -> service.getHourlyCsv(supplyId, YearMonth.of(2027, 1)));

        assertEquals(YearMonth.of(2026, 12), refused.getCurrentMonth());
        verifyNoInteractions(getSupplyRepository, getDatadisConsumptionRepository);
    }

    @Test
    @DisplayName("DCX-003 the current month in the application's time zone is read, and covers its local midnights half-open")
    void theCurrentMonthIsReadOverItsLocalMidnights() {
        Supply supply = SupplyMother.random().withCode("ES0031406912345678JN0F").build();
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));
        when(getDatadisConsumptionRepository.getHourlyRecordsByPeriod(any(), any(), any())).thenReturn(List.of());

        SupplyDatadisConsumptionCsv csv = service.getHourlyCsv(supplyId, YearMonth.of(2026, 12));

        verify(getDatadisConsumptionRepository).getHourlyRecordsByPeriod(supply,
                OffsetDateTime.parse("2026-12-01T00:00+01:00"), OffsetDateTime.parse("2027-01-01T00:00+01:00"));
        assertEquals("ES0031406912345678JN0F-2026-12.csv", csv.filename());
    }

    @Test
    @DisplayName("DCX-003 a month is bounded by its own local midnights when the offset changes inside it")
    void aMonthWithAnOffsetChangeIsBoundedByItsLocalMidnights() {
        Supply supply = SupplyMother.random().build();
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));
        when(getDatadisConsumptionRepository.getHourlyRecordsByPeriod(any(), any(), any())).thenReturn(List.of());

        service.getHourlyCsv(supplyId, YearMonth.of(2026, 10));

        verify(getDatadisConsumptionRepository).getHourlyRecordsByPeriod(supply,
                OffsetDateTime.parse("2026-10-01T00:00+02:00"), OffsetDateTime.parse("2026-11-01T00:00+01:00"));
    }

    @Test
    void anUnknownSupplyIsNotFound() {
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class, () -> service.getHourlyCsv(supplyId, YearMonth.of(2026, 9)));
        verifyNoInteractions(getDatadisConsumptionRepository);
    }
}
