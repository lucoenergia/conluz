package org.lucoenergia.conluz.infrastructure.consumption.shelly.aggregate;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.shelly.config.GetShellyConfigurationService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShellyConsumptionsHourlyAggregatorJobTest {

    @Test
    void run_whenShellyDisabled_shouldSkipAggregation() {
        ShellyConsumptionsHourlyAggregatorService mockService = mock(ShellyConsumptionsHourlyAggregatorService.class);
        GetShellyConfigurationService getShellyConfigurationService = mock(GetShellyConfigurationService.class);

        when(getShellyConfigurationService.isDisabled()).thenReturn(true);

        ShellyConsumptionsHourlyAggregatorJob job = new ShellyConsumptionsHourlyAggregatorJob(mockService, getShellyConfigurationService);

        job.run();

        verify(mockService, times(0)).aggregate(null, null);
    }

    @Test
    void run_whenShellyEnabled_shouldCallAggregation() {
        ShellyConsumptionsHourlyAggregatorService mockService = mock(ShellyConsumptionsHourlyAggregatorService.class);
        GetShellyConfigurationService getShellyConfigurationService = mock(GetShellyConfigurationService.class);

        when(getShellyConfigurationService.isDisabled()).thenReturn(false);

        ShellyConsumptionsHourlyAggregatorJob job = new ShellyConsumptionsHourlyAggregatorJob(mockService, getShellyConfigurationService);

        job.run();

        verify(mockService, times(1)).aggregate(any(), any());
    }
}
