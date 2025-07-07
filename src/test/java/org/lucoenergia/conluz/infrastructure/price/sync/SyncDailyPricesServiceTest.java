package org.lucoenergia.conluz.infrastructure.price.sync;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.domain.price.persist.PersistOmiePricesRepository;
import org.lucoenergia.conluz.domain.price.sync.SyncDailyPricesService;
import org.lucoenergia.conluz.infrastructure.price.get.GetPriceRepositoryRest;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

class SyncDailyPricesServiceTest {

    private final GetPriceRepositoryRest getPriceRepositoryRest = mock(GetPriceRepositoryRest.class);
    private final PersistOmiePricesRepository persistOmiePricesRepository = mock(PersistOmiePricesRepository.class);
    private final SyncDailyPricesService syncDailyPricesService = new SyncDailyPricesServiceImpl(getPriceRepositoryRest,
            persistOmiePricesRepository);

    /**
     * Validates the successful synchronization of prices within a date range.
     */
    @Test
    void testSyncDailyPricesByDateIntervalSuccess() {
        OffsetDateTime startDate = OffsetDateTime.parse("2025-07-01T00:00:00Z");
        OffsetDateTime endDate = OffsetDateTime.parse("2025-07-02T00:00:00Z");

        List<PriceByHour> mockPrices = List.of(
                mock(PriceByHour.class),
                mock(PriceByHour.class)
        );

        when(getPriceRepositoryRest.getPricesByRangeOfDates(startDate, endDate)).thenReturn(mockPrices);

        // Execute method
        syncDailyPricesService.syncDailyPricesByDateInterval(startDate, endDate);

        // Verify interactions
        verify(getPriceRepositoryRest, times(1)).getPricesByRangeOfDates(startDate, endDate);
        verify(persistOmiePricesRepository, times(1)).persistPrices(mockPrices);
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
        verifyNoInteractions(getPriceRepositoryRest, persistOmiePricesRepository);
    }

    /**
     * Tests synchronization when no prices are returned from the repository.
     */
    @Test
    void testSyncDailyPricesByDateIntervalNoPricesReturned() {
        OffsetDateTime startDate = OffsetDateTime.parse("2025-07-01T00:00:00Z");
        OffsetDateTime endDate = OffsetDateTime.parse("2025-07-02T00:00:00Z");

        when(getPriceRepositoryRest.getPricesByRangeOfDates(startDate, endDate)).thenReturn(Collections.emptyList());

        // Execute method
        syncDailyPricesService.syncDailyPricesByDateInterval(startDate, endDate);

        // Verify interactions
        verify(getPriceRepositoryRest, times(1)).getPricesByRangeOfDates(startDate, endDate);
        verify(persistOmiePricesRepository, times(1)).persistPrices(Collections.emptyList());
    }
}