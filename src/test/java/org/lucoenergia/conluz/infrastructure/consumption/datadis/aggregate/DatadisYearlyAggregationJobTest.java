package org.lucoenergia.conluz.infrastructure.consumption.datadis.aggregate;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisYearlyAggregationService;
import org.lucoenergia.conluz.domain.consumption.datadis.config.GetDatadisConfigurationService;
import org.mockito.Mockito;

import java.time.LocalDate;

import static org.mockito.Mockito.*;

class DatadisYearlyAggregationJobTest {

    @Test
    void testRun_ShouldCallServiceWithCurrentYear() {
        // Arrange
        DatadisYearlyAggregationService mockService = Mockito.mock(DatadisYearlyAggregationService.class);
        GetDatadisConfigurationService mockConfigService = Mockito.mock(GetDatadisConfigurationService.class);
        when(mockConfigService.isDisabled()).thenReturn(false);

        DatadisYearlyAggregationJob job = new DatadisYearlyAggregationJob(mockService, mockConfigService);

        LocalDate today = LocalDate.now();
        int year = today.getYear();

        // Act
        job.run();

        // Assert
        verify(mockService, times(1)).aggregateYearlyConsumptions(year);
    }

    @Test
    void testRun_ShouldSkipAggregationWhenDatadisIsDisabled() {
        // Arrange
        DatadisYearlyAggregationService mockService = Mockito.mock(DatadisYearlyAggregationService.class);
        GetDatadisConfigurationService mockConfigService = Mockito.mock(GetDatadisConfigurationService.class);
        when(mockConfigService.isDisabled()).thenReturn(true);

        DatadisYearlyAggregationJob job = new DatadisYearlyAggregationJob(mockService, mockConfigService);

        // Act
        job.run();

        // Assert
        verify(mockService, never()).aggregateYearlyConsumptions(anyInt());
    }
}
