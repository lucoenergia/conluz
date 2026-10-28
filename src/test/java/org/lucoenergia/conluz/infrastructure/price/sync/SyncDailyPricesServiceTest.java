package org.lucoenergia.conluz.infrastructure.price.sync;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.domain.price.persist.PersistOmiePricesRepository;
import org.lucoenergia.conluz.domain.price.sync.GetOmiePricesRepository;
import org.lucoenergia.conluz.domain.price.sync.OmieDayPrices;
import org.lucoenergia.conluz.domain.price.sync.SyncDailyPricesService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

class SyncDailyPricesServiceTest {

    private final GetOmiePricesRepository getOmiePricesRepository = mock(GetOmiePricesRepository.class);
    private final PersistOmiePricesRepository persistOmiePricesRepository = mock(PersistOmiePricesRepository.class);
    private final SyncDailyPricesService syncDailyPricesService = new SyncDailyPricesServiceImpl(getOmiePricesRepository,
            persistOmiePricesRepository);

    /**
     * Validates the successful synchronization of prices within a date range: every day up to the end date, which is
     * inclusive, and the prices of the served days stored together.
     */
    @Test
    void testSyncDailyPricesByDateIntervalSuccess() {
        OffsetDateTime startDate = OffsetDateTime.parse("2025-07-01T00:00:00Z");
        OffsetDateTime endDate = OffsetDateTime.parse("2025-07-03T00:00:00Z");

        PriceByHour first = new PriceByHour(0.1, OffsetDateTime.parse("2025-07-01T00:00:00+02:00"));
        PriceByHour second = new PriceByHour(0.2, OffsetDateTime.parse("2025-07-01T01:00:00+02:00"));
        PriceByHour third = new PriceByHour(0.3, OffsetDateTime.parse("2025-07-03T00:00:00+02:00"));
        when(getOmiePricesRepository.getPricesByDays(LocalDate.parse("2025-07-01"), LocalDate.parse("2025-07-04")))
                .thenReturn(List.of(
                        OmieDayPrices.served(LocalDate.parse("2025-07-01"), List.of(first, second)),
                        OmieDayPrices.notServed(LocalDate.parse("2025-07-02"), OmieDayPrices.Status.NOT_FOUND),
                        OmieDayPrices.served(LocalDate.parse("2025-07-03"), List.of(third))));

        // Execute method
        syncDailyPricesService.syncDailyPricesByDateInterval(startDate, endDate);

        // Verify interactions
        verify(getOmiePricesRepository, times(1))
                .getPricesByDays(LocalDate.parse("2025-07-01"), LocalDate.parse("2025-07-04"));
        verify(persistOmiePricesRepository, times(1)).persistPrices(List.of(first, second, third));
    }

    /**
     * Tests that prices are not synchronized when the start date is after the end date.
     */
    @Test
    void testSyncDailyPricesByDateIntervalStartDateAfterEndDate() {
        OffsetDateTime startDate = OffsetDateTime.parse("2025-07-03T00:00:00Z");
        OffsetDateTime endDate = OffsetDateTime.parse("2025-07-01T00:00:00Z");

        // Execute method
        syncDailyPricesService.syncDailyPricesByDateInterval(startDate, endDate);

        // Verify no interactions with the repositories
        verifyNoInteractions(getOmiePricesRepository, persistOmiePricesRepository);
    }

    /**
     * Tests synchronization when no day is served.
     */
    @Test
    void testSyncDailyPricesByDateIntervalNoPricesReturned() {
        OffsetDateTime startDate = OffsetDateTime.parse("2025-07-01T00:00:00Z");
        OffsetDateTime endDate = OffsetDateTime.parse("2025-07-02T00:00:00Z");

        when(getOmiePricesRepository.getPricesByDays(LocalDate.parse("2025-07-01"), LocalDate.parse("2025-07-03")))
                .thenReturn(List.of(
                        OmieDayPrices.notServed(LocalDate.parse("2025-07-01"), OmieDayPrices.Status.REJECTED),
                        OmieDayPrices.notServed(LocalDate.parse("2025-07-02"), OmieDayPrices.Status.FETCH_FAILED)));

        // Execute method
        syncDailyPricesService.syncDailyPricesByDateInterval(startDate, endDate);

        // Verify interactions
        verify(getOmiePricesRepository, times(1))
                .getPricesByDays(LocalDate.parse("2025-07-01"), LocalDate.parse("2025-07-03"));
        verify(persistOmiePricesRepository, times(1)).persistPrices(Collections.emptyList());
    }
}
