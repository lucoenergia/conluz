package org.lucoenergia.conluz.infrastructure.consumption.datadis.aggregate;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisMonthlyAggregationService;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.GetDatadisConfigurationService;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncWindow;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.Month;
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

class DatadisMonthlyAggregationJobTest {

    // Midday, so the date is the same in any JVM default zone this test may run under.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-09T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void testRun_ShouldAggregateEveryMonthOfTheSyncWindowOfAnEnabledCommunity() {
        DatadisMonthlyAggregationService mockService = Mockito.mock(DatadisMonthlyAggregationService.class);
        GetDatadisConfigurationService mockConfigService = Mockito.mock(GetDatadisConfigurationService.class);
        UUID communityId = UUID.randomUUID();
        when(mockConfigService.findAllEnabled()).thenReturn(List.of(enabledConfig(communityId)));

        DatadisMonthlyAggregationJob job = new DatadisMonthlyAggregationJob(mockService, CLOCK,
                mockConfigService, DatadisSyncWindow.DEFAULT);

        job.run();

        for (Month month = Month.OCTOBER; month != Month.JANUARY; month = month.plus(1)) {
            verify(mockService).aggregateMonthlyConsumptions(communityId, month, 2025);
        }
        for (Month month : Month.values()) {
            if (month.compareTo(Month.OCTOBER) <= 0) {
                verify(mockService).aggregateMonthlyConsumptions(communityId, month, 2026);
            }
        }
        verifyNoMoreInteractions(mockService);
    }

    @Test
    void testRun_ShouldSkipAggregationWhenNoEnabledConfigs() {
        DatadisMonthlyAggregationService mockService = Mockito.mock(DatadisMonthlyAggregationService.class);
        GetDatadisConfigurationService mockConfigService = Mockito.mock(GetDatadisConfigurationService.class);
        when(mockConfigService.findAllEnabled()).thenReturn(Collections.emptyList());

        DatadisMonthlyAggregationJob job = new DatadisMonthlyAggregationJob(mockService, CLOCK,
                mockConfigService, DatadisSyncWindow.DEFAULT);

        job.run();

        verify(mockService, never()).aggregateMonthlyConsumptions(any(UUID.class), any(Month.class), anyInt());
    }

    private static DatadisConfig enabledConfig(UUID communityId) {
        return new DatadisConfig.Builder()
                .setCommunityId(communityId).setEnabled(Boolean.TRUE)
                .setUsername("u").setPassword("p").build();
    }
}
