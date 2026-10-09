package org.lucoenergia.conluz.infrastructure.consumption.datadis.aggregate;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisYearlyAggregationService;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.GetDatadisConfigurationService;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncWindow;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class DatadisYearlyAggregationJobTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-09T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void testRun_ShouldAggregateEveryYearTheSyncWindowOverlapsForAnEnabledCommunity() {
        DatadisYearlyAggregationService mockService = Mockito.mock(DatadisYearlyAggregationService.class);
        GetDatadisConfigurationService mockConfigService = Mockito.mock(GetDatadisConfigurationService.class);
        UUID communityId = UUID.randomUUID();
        when(mockConfigService.findAllEnabled()).thenReturn(List.of(enabledConfig(communityId)));

        DatadisYearlyAggregationJob job = new DatadisYearlyAggregationJob(mockService, CLOCK, mockConfigService,
                DatadisSyncWindow.DEFAULT, zoneResolver(ZoneId.of("Europe/Madrid")));

        job.run();

        verify(mockService).aggregateYearlyConsumptions(communityId, 2025);
        verify(mockService).aggregateYearlyConsumptions(communityId, 2026);
        verifyNoMoreInteractions(mockService);
    }

    @Test
    void testRun_ShouldSkipAggregationWhenNoEnabledConfigs() {
        DatadisYearlyAggregationService mockService = Mockito.mock(DatadisYearlyAggregationService.class);
        GetDatadisConfigurationService mockConfigService = Mockito.mock(GetDatadisConfigurationService.class);
        when(mockConfigService.findAllEnabled()).thenReturn(Collections.emptyList());

        DatadisYearlyAggregationJob job = new DatadisYearlyAggregationJob(mockService, CLOCK, mockConfigService,
                DatadisSyncWindow.DEFAULT, zoneResolver(ZoneId.of("Europe/Madrid")));

        job.run();

        verify(mockService, never()).aggregateYearlyConsumptions(any(UUID.class), anyInt());
    }

    private static DatadisConfig enabledConfig(UUID communityId) {
        return new DatadisConfig.Builder()
                .setCommunityId(communityId).setEnabled(Boolean.TRUE)
                .setUsername("u").setPassword("p").build();
    }

    private static ZoneResolver zoneResolver(ZoneId zone) {
        ZoneResolver zoneResolver = Mockito.mock(ZoneResolver.class);
        when(zoneResolver.resolveZoneIdForCommunity(any(UUID.class))).thenReturn(zone);
        return zoneResolver;
    }
}
