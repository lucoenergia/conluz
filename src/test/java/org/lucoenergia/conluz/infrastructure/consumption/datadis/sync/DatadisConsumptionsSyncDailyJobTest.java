package org.lucoenergia.conluz.infrastructure.consumption.datadis.sync;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.datadis.sync.DatadisConsumptionSyncService;
import org.mockito.Mockito;

import java.time.LocalDate;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DatadisConsumptionsSyncDailyJobTest {

    @Test
    void testRun_ShouldCallSynchronizeConsumptionsWithCorrectDates() {
        // Arrange
        DatadisConsumptionSyncService mockSyncService = Mockito.mock(DatadisConsumptionSyncService.class);
        DatadisConsumptionsSyncDailyJob job = new DatadisConsumptionsSyncDailyJob(mockSyncService);

        LocalDate today = LocalDate.now();
        LocalDate oneYearAgo = today.minusYears(1).withDayOfMonth(1);

        // Act
        job.run();

        // Assert
        verify(mockSyncService, times(1)).synchronizeConsumptions(oneYearAgo, today);
    }
}