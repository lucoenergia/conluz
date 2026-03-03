package org.lucoenergia.conluz.infrastructure.consumption.datadis.aggregate;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisMonthlyAggregationService;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.Month;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DatadisMonthlyAggregationJobTest {

    @Test
    void testRun_ShouldCallServiceWithPreviousMonth() {
        // Arrange
        DatadisMonthlyAggregationService mockService = Mockito.mock(DatadisMonthlyAggregationService.class);
        DatadisMonthlyAggregationJob job = new DatadisMonthlyAggregationJob(mockService);

        LocalDate today = LocalDate.now();
        Month month = today.getMonth();
        int year = today.getYear();

        // Act
        job.run();

        // Assert
        verify(mockService, times(1)).aggregateMonthlyConsumptions(month, year);
    }
}
