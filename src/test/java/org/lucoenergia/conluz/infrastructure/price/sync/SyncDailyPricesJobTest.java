package org.lucoenergia.conluz.infrastructure.price.sync;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.price.sync.SyncDailyPricesService;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;
import org.mockito.Mockito;

import java.time.OffsetDateTime;

import static org.mockito.Mockito.*;

class SyncDailyPricesJobTest {

    private final TimeConfiguration mockTimeConfiguration = Mockito.mock(TimeConfiguration.class);
    private final SyncDailyPricesService mockSyncDailyPricesService = Mockito.mock(SyncDailyPricesService.class);
    private final SyncDailyPricesJob job = new SyncDailyPricesJob(mockTimeConfiguration, mockSyncDailyPricesService);

    @Test
    void testRunExecutesSyncDailyPricesByDateIntervalCorrectly() {
        // Arrange
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime oneWeekAgo = now.minusWeeks(1);

        when(mockTimeConfiguration.now()).thenReturn(now);

        // Act
        job.run();

        // Assert
        verify(mockTimeConfiguration, times(1)).now();
        verify(mockSyncDailyPricesService, times(1))
                .syncDailyPricesByDateInterval(oneWeekAgo, now);
    }
}