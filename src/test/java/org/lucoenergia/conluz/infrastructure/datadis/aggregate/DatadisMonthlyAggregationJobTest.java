package org.lucoenergia.conluz.infrastructure.datadis.aggregate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisMonthlyAggregationService;
import org.lucoenergia.conluz.domain.datadis.GetDatadisConfigurationService;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncWindow;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Month;
import java.time.Period;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import static org.lucoenergia.conluz.infrastructure.datadis.DatadisJobTestSupport.MADRID;
import static org.lucoenergia.conluz.infrastructure.datadis.DatadisJobTestSupport.clockAt;
import static org.lucoenergia.conluz.infrastructure.datadis.DatadisJobTestSupport.enabledConfig;
import static org.lucoenergia.conluz.infrastructure.datadis.DatadisJobTestSupport.zoneResolver;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class DatadisMonthlyAggregationJobTest {

    private static final UUID COMMUNITY_A = UUID.randomUUID();
    private static final UUID COMMUNITY_B = UUID.randomUUID();
    private static final UUID DISABLED_COMMUNITY = UUID.randomUUID();

    private final DatadisMonthlyAggregationService service = Mockito.mock(DatadisMonthlyAggregationService.class);
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
    @DisplayName("DCA-001 every month of the window is re-aggregated for every Datadis-enabled community, and nothing else")
    void everyMonthOfTheWindowIsReaggregatedForEveryEnabledCommunity() {
        job(clockAt("2026-10-09T03:00:00Z"), DatadisSyncWindow.DEFAULT).run();

        for (UUID community : List.of(COMMUNITY_A, COMMUNITY_B)) {
            InOrder inOrder = inOrder(service);
            for (YearMonth month = YearMonth.of(2025, 10); !month.isAfter(YearMonth.of(2026, 10));
                 month = month.plusMonths(1)) {
                inOrder.verify(service).aggregateMonthlyConsumptions(community, month.getMonth(), month.getYear());
            }
        }
        verify(service, never()).aggregateMonthlyConsumptions(eq(DISABLED_COMMUNITY), any(Month.class), anyInt());
        // In particular, September 2025, the month before the window, is never rewritten.
        verifyNoMoreInteractions(service);
    }

    @Test
    @ResourceLock(Resources.TIME_ZONE)
    @DisplayName("DCA-001 the current month is resolved in the community's zone, not the JVM default (00:30 on 1 October in Madrid)")
    void theCurrentMonthIsResolvedInTheCommunityZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        // 2026-09-30T22:30Z is still September in UTC, already 2026-10-01 00:30 in Madrid.
        job(clockAt("2026-09-30T22:30:00Z"), DatadisSyncWindow.DEFAULT).run();

        verify(service).aggregateMonthlyConsumptions(COMMUNITY_A, Month.OCTOBER, 2026);
        verify(service, never()).aggregateMonthlyConsumptions(COMMUNITY_A, Month.SEPTEMBER, 2025);
        verify(service).aggregateMonthlyConsumptions(COMMUNITY_A, Month.OCTOBER, 2025);
    }

    @Test
    @ResourceLock(Resources.TIME_ZONE)
    @DisplayName("DCA-001 the current month is resolved in the community's zone across the CET boundary (00:30 on 1 November in Madrid)")
    void theCurrentMonthIsResolvedInTheCommunityZoneInWinterTime() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        // 2026-10-31T23:30Z is still October in UTC, already 2026-11-01 00:30 CET in Madrid.
        job(clockAt("2026-10-31T23:30:00Z"), DatadisSyncWindow.DEFAULT).run();

        verify(service).aggregateMonthlyConsumptions(COMMUNITY_A, Month.NOVEMBER, 2026);
        verify(service).aggregateMonthlyConsumptions(COMMUNITY_A, Month.NOVEMBER, 2025);
        verify(service, never()).aggregateMonthlyConsumptions(COMMUNITY_A, Month.OCTOBER, 2025);
    }

    @Test
    @DisplayName("DCA-001 the aggregation window follows the sync window, with no second value to change")
    void theAggregationWindowFollowsTheSyncWindow() {
        job(clockAt("2026-10-09T03:00:00Z"), new DatadisSyncWindow(Period.ofMonths(3))).run();

        for (UUID community : List.of(COMMUNITY_A, COMMUNITY_B)) {
            for (Month month : List.of(Month.JULY, Month.AUGUST, Month.SEPTEMBER, Month.OCTOBER)) {
                verify(service).aggregateMonthlyConsumptions(community, month, 2026);
            }
        }
        verifyNoMoreInteractions(service);
    }

    @Test
    void aFailingCommunityDoesNotStopTheOthers() {
        doThrow(new RuntimeException("boom"))
                .when(service).aggregateMonthlyConsumptions(eq(COMMUNITY_A), any(Month.class), anyInt());

        job(clockAt("2026-10-09T03:00:00Z"), DatadisSyncWindow.DEFAULT).run();

        verify(service).aggregateMonthlyConsumptions(COMMUNITY_B, Month.OCTOBER, 2026);
    }

    @Test
    void nothingIsAggregatedWithoutEnabledConfigs() {
        when(configService.findAllEnabled()).thenReturn(Collections.emptyList());

        job(clockAt("2026-10-09T03:00:00Z"), DatadisSyncWindow.DEFAULT).run();

        verifyNoMoreInteractions(service);
    }

    private DatadisMonthlyAggregationJob job(Clock clock, DatadisSyncWindow window) {
        return new DatadisMonthlyAggregationJob(service, clock, configService, window, zoneResolver(MADRID));
    }
}
