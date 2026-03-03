package org.lucoenergia.conluz.infrastructure.consumption.datadis.aggregate;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisYearlyAggregationService;
import org.mockito.Mockito;

import java.time.LocalDate;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DatadisYearlyAggregationJobTest {

    @Test
    void testRun_ShouldCallServiceWithPreviousYear() {
        // Arrange
        DatadisYearlyAggregationService mockService = Mockito.mock(DatadisYearlyAggregationService.class);
        DatadisYearlyAggregationJob job = new DatadisYearlyAggregationJob(mockService);

        LocalDate today = LocalDate.now();
        int year = today.getYear();

        // Act
        job.run();

        // Assert
        verify(mockService, times(1)).aggregateYearlyConsumptions(year);
    }
}
