package org.lucoenergia.conluz.infrastructure.consumption.datadis.aggregate;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisMonthlyAggregationService;
import org.lucoenergia.conluz.domain.consumption.datadis.config.GetDatadisConfigurationService;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.Month;

import static org.mockito.Mockito.*;

class DatadisMonthlyAggregationJobTest {

    @Test
    void testRun_ShouldCallServiceWithCurrentMonthAndYear() {
        // Arrange
        DatadisMonthlyAggregationService mockService = Mockito.mock(DatadisMonthlyAggregationService.class);
        GetDatadisConfigurationService mockConfigService = Mockito.mock(GetDatadisConfigurationService.class);
        when(mockConfigService.isDisabled()).thenReturn(false);

        DatadisMonthlyAggregationJob job = new DatadisMonthlyAggregationJob(mockService, mockConfigService);

        LocalDate today = LocalDate.now();
        Month month = today.getMonth();
        int year = today.getYear();

        // Act
        job.run();

        // Assert
        verify(mockService, times(1)).aggregateMonthlyConsumptions(month, year);
    }

    @Test
    void testRun_ShouldSkipAggregationWhenDatadisIsDisabled() {
        // Arrange
        DatadisMonthlyAggregationService mockService = Mockito.mock(DatadisMonthlyAggregationService.class);
        GetDatadisConfigurationService mockConfigService = Mockito.mock(GetDatadisConfigurationService.class);
        when(mockConfigService.isDisabled()).thenReturn(true);

        DatadisMonthlyAggregationJob job = new DatadisMonthlyAggregationJob(mockService, mockConfigService);

        // Act
        job.run();

        // Assert
        verify(mockService, never()).aggregateMonthlyConsumptions(any(), anyInt());
    }
}
