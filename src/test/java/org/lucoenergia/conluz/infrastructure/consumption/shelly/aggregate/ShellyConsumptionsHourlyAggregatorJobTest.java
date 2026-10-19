package org.lucoenergia.conluz.infrastructure.consumption.shelly.aggregate;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.shelly.config.GetShellyConfigurationService;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShellyConsumptionsHourlyAggregatorJobTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T12:34:56Z"), ZoneOffset.UTC);

    @Test
    void run_whenShellyDisabled_shouldSkipAggregation() {
        ShellyConsumptionsHourlyAggregatorService mockService = mock(ShellyConsumptionsHourlyAggregatorService.class);
        GetShellyConfigurationService getShellyConfigurationService = mock(GetShellyConfigurationService.class);

        when(getShellyConfigurationService.isDisabled()).thenReturn(true);

        ShellyConsumptionsHourlyAggregatorJob job = new ShellyConsumptionsHourlyAggregatorJob(mockService, getShellyConfigurationService,
                CLOCK);

        job.run();

        verify(mockService, times(0)).aggregate(null, null);
    }

    @Test
    void run_whenShellyEnabled_shouldCallAggregation() {
        ShellyConsumptionsHourlyAggregatorService mockService = mock(ShellyConsumptionsHourlyAggregatorService.class);
        GetShellyConfigurationService getShellyConfigurationService = mock(GetShellyConfigurationService.class);

        when(getShellyConfigurationService.isDisabled()).thenReturn(false);

        ShellyConsumptionsHourlyAggregatorJob job = new ShellyConsumptionsHourlyAggregatorJob(mockService, getShellyConfigurationService,
                CLOCK);

        job.run();

        verify(mockService, times(1)).aggregate(
                OffsetDateTime.parse("2026-10-19T07:00:00Z"), OffsetDateTime.parse("2026-10-19T12:00:00Z"));
    }
}
