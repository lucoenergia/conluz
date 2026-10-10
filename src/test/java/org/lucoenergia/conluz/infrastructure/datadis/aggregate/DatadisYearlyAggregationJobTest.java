package org.lucoenergia.conluz.infrastructure.datadis.aggregate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisYearlyAggregationService;
import org.lucoenergia.conluz.domain.datadis.GetDatadisConfigurationService;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncWindow;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Period;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import static org.lucoenergia.conluz.infrastructure.datadis.DatadisJobTestSupport.MADRID;
import static org.lucoenergia.conluz.infrastructure.datadis.DatadisJobTestSupport.clockAt;
import static org.lucoenergia.conluz.infrastructure.datadis.DatadisJobTestSupport.enabledConfig;
import static org.lucoenergia.conluz.infrastructure.datadis.DatadisJobTestSupport.zoneResolver;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class DatadisYearlyAggregationJobTest {

    private static final UUID COMMUNITY_A = UUID.randomUUID();
    private static final UUID COMMUNITY_B = UUID.randomUUID();
    private static final UUID DISABLED_COMMUNITY = UUID.randomUUID();

    private final DatadisYearlyAggregationService service = Mockito.mock(DatadisYearlyAggregationService.class);
    private final GetDatadisConfigurationService configService = Mockito.mock(GetDatadisConfigurationService.class);

    private TimeZone originalDefaultZone;

    @BeforeEach
    void setUp() {
        originalDefaultZone = TimeZone.getDefault();
        // Only enabled configs are returned: DISABLED_COMMUNITY has a config with enabled = false.
        when(configService.findAllEnabled()).thenReturn(List.of(enabledConfig(COMMUNITY_A), enabledConfig(COMMUNITY_B)));
    }

    @AfterEach
    void restoreDefaultZone() {
        TimeZone.setDefault(originalDefaultZone);
    }

    @Test
    @DisplayName("DCA-003 every year the window overlaps is re-aggregated for every Datadis-enabled community, and nothing else")
    void everyYearTheWindowOverlapsIsReaggregatedForEveryEnabledCommunity() {
        // The window on 2026-10-09 is 2025-10 to 2026-10.
        job(clockAt("2026-10-09T04:00:00Z"), DatadisSyncWindow.DEFAULT).run();

        for (UUID community : List.of(COMMUNITY_A, COMMUNITY_B)) {
            verify(service).aggregateYearlyConsumptions(community, 2025);
            verify(service).aggregateYearlyConsumptions(community, 2026);
        }
        verify(service, never()).aggregateYearlyConsumptions(eq(DISABLED_COMMUNITY), anyInt());
        // In particular, 2024, before the window, is never rewritten.
        verifyNoMoreInteractions(service);
    }

    @Test
    @ResourceLock(Resources.TIME_ZONE)
    @DisplayName("DCA-003 the current year is resolved in the community's zone, not the JVM default (00:30 on 1 January in Madrid)")
    void theCurrentYearIsResolvedInTheCommunityZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        // 2026-12-31T23:30Z is still 2026 in UTC, already 2027-01-01 00:30 in Madrid: the window is
        // 2026-01 to 2027-01.
        job(clockAt("2026-12-31T23:30:00Z"), DatadisSyncWindow.DEFAULT).run();

        verify(service).aggregateYearlyConsumptions(COMMUNITY_A, 2026);
        verify(service).aggregateYearlyConsumptions(COMMUNITY_A, 2027);
        verify(service, never()).aggregateYearlyConsumptions(COMMUNITY_A, 2025);
    }

    @Test
    @DisplayName("DCA-003 the years follow the sync window, with no second value to change")
    void theYearsFollowTheSyncWindow() {
        job(clockAt("2026-10-09T04:00:00Z"), new DatadisSyncWindow(Period.ofMonths(3))).run();

        verify(service).aggregateYearlyConsumptions(COMMUNITY_A, 2026);
        verify(service).aggregateYearlyConsumptions(COMMUNITY_B, 2026);
        verifyNoMoreInteractions(service);
    }

    @Test
    void aFailingCommunityDoesNotStopTheOthers() {
        doThrow(new RuntimeException("boom")).when(service).aggregateYearlyConsumptions(eq(COMMUNITY_A), anyInt());

        job(clockAt("2026-10-09T04:00:00Z"), DatadisSyncWindow.DEFAULT).run();

        verify(service).aggregateYearlyConsumptions(COMMUNITY_B, 2026);
    }

    @Test
    void nothingIsAggregatedWithoutEnabledConfigs() {
        when(configService.findAllEnabled()).thenReturn(Collections.emptyList());

        job(clockAt("2026-10-09T04:00:00Z"), DatadisSyncWindow.DEFAULT).run();

        verifyNoMoreInteractions(service);
    }

    private DatadisYearlyAggregationJob job(Clock clock, DatadisSyncWindow window) {
        return new DatadisYearlyAggregationJob(service, clock, configService, window, zoneResolver(MADRID));
    }
}
