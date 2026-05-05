package org.lucoenergia.conluz.infrastructure.production.huawei.aggregate;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionMonthlyAggregationService;
import org.lucoenergia.conluz.domain.production.huawei.config.GetHuaweiConfigurationService;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.Month;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class HuaweiProductionMonthlyAggregationJobTest {

    @Test
    void testRun_ShouldCallServiceWithCurrentMonthAndYear() {
        // Arrange
        HuaweiProductionMonthlyAggregationService mockService = Mockito.mock(HuaweiProductionMonthlyAggregationService.class);
        GetHuaweiConfigurationService getHuaweiConfigurationService = Mockito.mock(GetHuaweiConfigurationService.class);
        HuaweiProductionMonthlyAggregationJob job = new HuaweiProductionMonthlyAggregationJob(mockService, getHuaweiConfigurationService);

        LocalDate today = LocalDate.now();
        Month month = today.getMonth();
        int year = today.getYear();

        // Act
        job.run();

        // Assert
        verify(mockService, times(1)).aggregateMonthlyProductions(month, year);
    }
}
