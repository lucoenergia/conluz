package org.lucoenergia.conluz.infrastructure.consumption.datadis.sync;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.datadis.config.GetDatadisConfigurationService;
import org.lucoenergia.conluz.domain.consumption.datadis.sync.DatadisConsumptionSyncService;
import org.mockito.Mockito;

import java.time.LocalDate;

import static org.mockito.Mockito.*;

class DatadisConsumptionsSyncDailyJobTest {

    @Test
    void testRun_ShouldCallSynchronizeConsumptionsWithCorrectDates() {
        // Arrange
        DatadisConsumptionSyncService mockSyncService = Mockito.mock(DatadisConsumptionSyncService.class);
        GetDatadisConfigurationService mockConfigService = Mockito.mock(GetDatadisConfigurationService.class);
        when(mockConfigService.isDisabled()).thenReturn(false);

        DatadisConsumptionsSyncDailyJob job = new DatadisConsumptionsSyncDailyJob(mockSyncService, mockConfigService);

        LocalDate today = LocalDate.now();
        LocalDate oneYearAgo = today.minusYears(1).withDayOfMonth(1);

        // Act
        job.run();

        // Assert
        verify(mockSyncService, times(1)).synchronizeConsumptions(oneYearAgo, today);
    }

    @Test
    void testRun_ShouldSkipSyncWhenDatadisIsDisabled() {
        // Arrange
        DatadisConsumptionSyncService mockSyncService = Mockito.mock(DatadisConsumptionSyncService.class);
        GetDatadisConfigurationService mockConfigService = Mockito.mock(GetDatadisConfigurationService.class);
        when(mockConfigService.isDisabled()).thenReturn(true);

        DatadisConsumptionsSyncDailyJob job = new DatadisConsumptionsSyncDailyJob(mockSyncService, mockConfigService);

        // Act
        job.run();

        // Assert
        verify(mockSyncService, never()).synchronizeConsumptions(any(), any());
    }
}
