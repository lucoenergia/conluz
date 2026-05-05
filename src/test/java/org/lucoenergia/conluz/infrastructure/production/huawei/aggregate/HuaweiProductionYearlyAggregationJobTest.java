package org.lucoenergia.conluz.infrastructure.production.huawei.aggregate;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionYearlyAggregationService;
import org.lucoenergia.conluz.domain.production.huawei.config.GetHuaweiConfigurationService;
import org.mockito.Mockito;

import java.time.LocalDate;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class HuaweiProductionYearlyAggregationJobTest {

    @Test
    void testRun_ShouldCallServiceWithCurrentYear() {
        // Arrange
        HuaweiProductionYearlyAggregationService mockService = Mockito.mock(HuaweiProductionYearlyAggregationService.class);
        GetHuaweiConfigurationService getHuaweiConfigurationService = Mockito.mock(GetHuaweiConfigurationService.class);
        HuaweiProductionYearlyAggregationJob job = new HuaweiProductionYearlyAggregationJob(mockService, getHuaweiConfigurationService);

        LocalDate today = LocalDate.now();
        int year = today.getYear();

        // Act
        job.run();

        // Assert
        verify(mockService, times(1)).aggregateYearlyProductions(year);
    }
}
