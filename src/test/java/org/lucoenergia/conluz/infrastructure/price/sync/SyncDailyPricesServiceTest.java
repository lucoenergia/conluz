package org.lucoenergia.conluz.infrastructure.price.sync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.domain.price.persist.PersistOmiePricesRepository;
import org.lucoenergia.conluz.domain.price.sync.GetOmiePricesRepository;
import org.lucoenergia.conluz.domain.price.sync.InvalidPriceSyncPeriodException;
import org.lucoenergia.conluz.domain.price.sync.OmieDayPrices;
import org.lucoenergia.conluz.domain.price.sync.PriceSyncResult;
import org.lucoenergia.conluz.domain.price.sync.SyncDailyPricesService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class SyncDailyPricesServiceTest {

    private final GetOmiePricesRepository getOmiePricesRepository = mock(GetOmiePricesRepository.class);
    private final PersistOmiePricesRepository persistOmiePricesRepository = mock(PersistOmiePricesRepository.class);
    // 00:30 on 2026-04-01 in Madrid, still 2026-03-31 in UTC: tomorrow is 2026-04-02 in Madrid
    private final Clock clock = Clock.fixed(Instant.parse("2026-03-31T22:30:00Z"), ZoneOffset.UTC);
    private final SyncDailyPricesService syncDailyPricesService = new SyncDailyPricesServiceImpl(getOmiePricesRepository,
            persistOmiePricesRepository, clock);

    @Test
    @DisplayName("PRC-006 each day of the range is reported in date order, stored with its count or skipped with its reason")
    void syncDailyPricesByDays_reportsEachDayAndStoresTheServedOnes() {
        PriceByHour first = new PriceByHour(0.1, OffsetDateTime.parse("2026-03-27T00:00:00+01:00"));
        PriceByHour second = new PriceByHour(0.2, OffsetDateTime.parse("2026-03-27T00:15:00+01:00"));
        PriceByHour third = new PriceByHour(0.3, OffsetDateTime.parse("2026-03-31T00:00:00+02:00"));
        LocalDate start = LocalDate.parse("2026-03-27");
        LocalDate end = LocalDate.parse("2026-04-01");
        when(getOmiePricesRepository.getPricesByDays(start, end)).thenReturn(List.of(
                OmieDayPrices.served(LocalDate.parse("2026-03-27"), List.of(first, second)),
                OmieDayPrices.notServed(LocalDate.parse("2026-03-28"), OmieDayPrices.Status.NOT_FOUND),
                OmieDayPrices.notServed(LocalDate.parse("2026-03-29"), OmieDayPrices.Status.REJECTED),
                OmieDayPrices.notServed(LocalDate.parse("2026-03-30"), OmieDayPrices.Status.FETCH_FAILED),
                OmieDayPrices.served(LocalDate.parse("2026-03-31"), List.of(third))));

        List<PriceSyncResult> results = syncDailyPricesService.syncDailyPricesByDays(start, end);

        assertEquals(List.of(
                new PriceSyncResult(LocalDate.parse("2026-03-27"), PriceSyncResult.Outcome.STORED, 2, null),
                new PriceSyncResult(LocalDate.parse("2026-03-28"), PriceSyncResult.Outcome.SKIPPED, 0,
                        PriceSyncResult.SkipReason.FILE_NOT_FOUND),
                new PriceSyncResult(LocalDate.parse("2026-03-29"), PriceSyncResult.Outcome.SKIPPED, 0,
                        PriceSyncResult.SkipReason.FILE_REJECTED),
                new PriceSyncResult(LocalDate.parse("2026-03-30"), PriceSyncResult.Outcome.SKIPPED, 0,
                        PriceSyncResult.SkipReason.FETCH_FAILED),
                new PriceSyncResult(LocalDate.parse("2026-03-31"), PriceSyncResult.Outcome.STORED, 1, null)),
                results);
        verify(persistOmiePricesRepository, times(1)).persistPrices(List.of(first, second, third));
    }

    @Test
    void syncDailyPricesByDays_writesNothingWhenNoDayIsServed() {
        LocalDate start = LocalDate.parse("2026-03-28");
        LocalDate end = LocalDate.parse("2026-03-30");
        when(getOmiePricesRepository.getPricesByDays(start, end)).thenReturn(List.of(
                OmieDayPrices.notServed(LocalDate.parse("2026-03-28"), OmieDayPrices.Status.NOT_FOUND),
                OmieDayPrices.notServed(LocalDate.parse("2026-03-29"), OmieDayPrices.Status.NOT_FOUND)));

        List<PriceSyncResult> results = syncDailyPricesService.syncDailyPricesByDays(start, end);

        assertEquals(2, results.size());
        verifyNoInteractions(persistOmiePricesRepository);
    }

    @ParameterizedTest(name = "[{0}, {1}) is rejected with {2} and nothing is fetched")
    @CsvSource({
            "2026-03-28, 2026-03-28, START_AFTER_END",
            "2026-03-29, 2026-03-28, START_AFTER_END",
            "2026-02-01, 2026-03-05, TOO_LONG",
            // tomorrow in Madrid is 2026-04-02, although the clock's own date is 2026-03-31
            "2026-03-28, 2026-04-04, END_BEYOND_TOMORROW"
    })
    @DisplayName("PRC-005 a range that may not be synchronised is rejected before anything is fetched")
    void syncDailyPricesByDays_rejectsAnInvalidRangeBeforeFetching(LocalDate start, LocalDate end,
                                                                   InvalidPriceSyncPeriodException.Reason reason) {
        InvalidPriceSyncPeriodException exception = assertThrows(InvalidPriceSyncPeriodException.class,
                () -> syncDailyPricesService.syncDailyPricesByDays(start, end));

        assertEquals(reason, exception.getReason());
        verifyNoInteractions(getOmiePricesRepository, persistOmiePricesRepository);
    }

    @Test
    @DisplayName("PRC-005 tomorrow is read in Madrid, so a range ending the day after tomorrow there is accepted")
    void syncDailyPricesByDays_acceptsARangeUpToTomorrowInMadrid() {
        LocalDate start = LocalDate.parse("2026-04-02");
        LocalDate end = LocalDate.parse("2026-04-03");
        when(getOmiePricesRepository.getPricesByDays(start, end)).thenReturn(List.of(
                OmieDayPrices.notServed(start, OmieDayPrices.Status.NOT_FOUND)));

        List<PriceSyncResult> results = syncDailyPricesService.syncDailyPricesByDays(start, end);

        assertEquals(List.of(new PriceSyncResult(start, PriceSyncResult.Outcome.SKIPPED, 0,
                PriceSyncResult.SkipReason.FILE_NOT_FOUND)), results);
    }

    @Test
    void syncDailyPricesByDays_propagatesAFailedWrite() {
        LocalDate start = LocalDate.parse("2026-03-28");
        LocalDate end = LocalDate.parse("2026-03-29");
        when(getOmiePricesRepository.getPricesByDays(start, end)).thenReturn(List.of(OmieDayPrices.served(start,
                List.of(new PriceByHour(0.1, OffsetDateTime.parse("2026-03-28T00:00:00+01:00"))))));
        doThrow(new IllegalStateException("InfluxDB is down")).when(persistOmiePricesRepository).persistPrices(anyList());

        assertThrows(IllegalStateException.class, () -> syncDailyPricesService.syncDailyPricesByDays(start, end));
    }
}
