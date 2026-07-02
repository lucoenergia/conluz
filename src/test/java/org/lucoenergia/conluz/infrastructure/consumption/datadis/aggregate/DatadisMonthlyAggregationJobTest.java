package org.lucoenergia.conluz.infrastructure.consumption.datadis.aggregate;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisMonthlyAggregationService;
import org.lucoenergia.conluz.domain.datadis.GetDatadisConfigurationService;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.Month;

import static org.mockito.Mockito.*;

class DatadisMonthlyAggregationJobTest {

    @Test
    void testRun_ShouldCallServiceWithCurrentMonthAndYear() {
        DatadisMonthlyAggregationService mockService = Mockito.mock(DatadisMonthlyAggregationService.class);
        GetDatadisConfigurationService mockConfigService = Mockito.mock(GetDatadisConfigurationService.class);

        DatadisMonthlyAggregationJob job = new DatadisMonthlyAggregationJob(mockService, mockConfigService);

        LocalDate today = LocalDate.now();
        Month month = today.getMonth();
        int year = today.getYear();

        job.run();

        verify(mockService, times(1)).aggregateMonthlyConsumptions(month, year);
    }
}
