package org.lucoenergia.conluz.infrastructure.consumption.shelly.parse;

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

class ShellyMqttPowerMessagesToInstantConsumptionsProcessorJobTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T12:34:56Z"), ZoneOffset.UTC);

    @Test
    void run_whenShellyDisabled_shouldSkipProcessing() {
        ShellyMqttPowerMessagesToInstantConsumptionsProcessor mockProcessor = mock(ShellyMqttPowerMessagesToInstantConsumptionsProcessor.class);
        GetShellyConfigurationService getShellyConfigurationService = mock(GetShellyConfigurationService.class);

        when(getShellyConfigurationService.isDisabled()).thenReturn(true);

        ShellyMqttPowerMessagesToInstantConsumptionsProcessorJob job = new ShellyMqttPowerMessagesToInstantConsumptionsProcessorJob(mockProcessor, getShellyConfigurationService,
                CLOCK);

        job.run();

        verify(mockProcessor, times(0)).process(null, null);
    }

    @Test
    void run_whenShellyEnabled_shouldCallProcessing() {
        ShellyMqttPowerMessagesToInstantConsumptionsProcessor mockProcessor = mock(ShellyMqttPowerMessagesToInstantConsumptionsProcessor.class);
        GetShellyConfigurationService getShellyConfigurationService = mock(GetShellyConfigurationService.class);

        when(getShellyConfigurationService.isDisabled()).thenReturn(false);

        ShellyMqttPowerMessagesToInstantConsumptionsProcessorJob job = new ShellyMqttPowerMessagesToInstantConsumptionsProcessorJob(mockProcessor, getShellyConfigurationService,
                CLOCK);

        job.run();

        verify(mockProcessor, times(1)).process(
                OffsetDateTime.parse("2026-10-19T12:29:00Z"), OffsetDateTime.parse("2026-10-19T12:34:00Z"));
    }
}
