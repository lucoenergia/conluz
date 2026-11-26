package org.lucoenergia.conluz.infrastructure.production.datadis.aggregate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.lucoenergia.conluz.domain.datadis.GetDatadisConfigurationService;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncWindow;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncWindows;
import org.lucoenergia.conluz.domain.production.datadis.aggregate.DatadisProductionMonthlyAggregationService;
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

class DatadisProductionMonthlyAggregationJobTest {

    private static final UUID COMMUNITY_A = UUID.randomUUID();
    private static final UUID COMMUNITY_B = UUID.randomUUID();
    private static final UUID DISABLED_COMMUNITY = UUID.randomUUID();

    private final DatadisProductionMonthlyAggregationService service =
            Mockito.mock(DatadisProductionMonthlyAggregationService.class);
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
    @DisplayName("DCA-002 on a weekday every month of the full window is re-aggregated for every Datadis-enabled community, and nothing else")
    void everyMonthOfTheWindowIsReaggregatedForEveryEnabledCommunity() {
        // Thursday 2026-11-26, 04:00 in Madrid: the sync re-read only the daily window.
        job(clockAt("2026-11-26T03:00:00Z"), DatadisSyncWindows.DEFAULT).run();

        for (UUID community : List.of(COMMUNITY_A, COMMUNITY_B)) {
            InOrder inOrder = inOrder(service);
            for (YearMonth month = YearMonth.of(2024, 11); !month.isAfter(YearMonth.of(2026, 11));
                 month = month.plusMonths(1)) {
                inOrder.verify(service).aggregateMonthlyProductions(community, month.getMonth(), month.getYear());
            }
        }
        verify(service, never()).aggregateMonthlyProductions(eq(DISABLED_COMMUNITY), any(Month.class), anyInt());
        // In particular, October 2024, the month before the window, is never rewritten.
        verifyNoMoreInteractions(service);
    }

    @Test
    @DisplayName("DCA-002 on a Sunday the same full window is re-aggregated as on a weekday")
    void onASundayTheSameFullWindowIsReaggregated() {
        // Sunday 2026-11-29, 04:00 in Madrid: the sync re-read the full window.
        job(clockAt("2026-11-29T03:00:00Z"), DatadisSyncWindows.DEFAULT).run();

        for (UUID community : List.of(COMMUNITY_A, COMMUNITY_B)) {
            InOrder inOrder = inOrder(service);
            for (YearMonth month = YearMonth.of(2024, 11); !month.isAfter(YearMonth.of(2026, 11));
                 month = month.plusMonths(1)) {
                inOrder.verify(service).aggregateMonthlyProductions(community, month.getMonth(), month.getYear());
            }
        }
        verifyNoMoreInteractions(service);
    }

    @Test
    @ResourceLock(Resources.TIME_ZONE)
    @DisplayName("DCA-002 the current month is resolved in the community's zone, not the JVM default")
    void theCurrentMonthIsResolvedInTheCommunityZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        job(clockAt("2026-09-30T22:30:00Z"), DatadisSyncWindows.DEFAULT).run();
        verify(service).aggregateMonthlyProductions(COMMUNITY_A, Month.OCTOBER, 2026);
        verify(service).aggregateMonthlyProductions(COMMUNITY_A, Month.OCTOBER, 2024);
        verify(service, never()).aggregateMonthlyProductions(COMMUNITY_A, Month.SEPTEMBER, 2024);

        job(clockAt("2026-10-31T23:30:00Z"), DatadisSyncWindows.DEFAULT).run();
        verify(service).aggregateMonthlyProductions(COMMUNITY_A, Month.NOVEMBER, 2026);
    }

    @Test
    @DisplayName("DCA-002 the aggregation window is the full re-sync window, not the daily one, with no second value to change")
    void theAggregationWindowFollowsTheSyncWindow() {
        // A full window of 3 months next to a daily one of 1 month: only the full one counts.
        DatadisSyncWindows windows = new DatadisSyncWindows(new DatadisSyncWindow(Period.ofMonths(1)),
                new DatadisSyncWindow(Period.ofMonths(3)));
        job(clockAt("2026-10-09T03:00:00Z"), windows).run();

        for (UUID community : List.of(COMMUNITY_A, COMMUNITY_B)) {
            for (Month month : List.of(Month.JULY, Month.AUGUST, Month.SEPTEMBER, Month.OCTOBER)) {
                verify(service).aggregateMonthlyProductions(community, month, 2026);
            }
        }
        verifyNoMoreInteractions(service);
    }

    @Test
    void aFailingCommunityDoesNotStopTheOthers() {
        doThrow(new RuntimeException("boom"))
                .when(service).aggregateMonthlyProductions(eq(COMMUNITY_A), any(Month.class), anyInt());

        job(clockAt("2026-11-26T03:00:00Z"), DatadisSyncWindows.DEFAULT).run();

        verify(service).aggregateMonthlyProductions(COMMUNITY_B, Month.NOVEMBER, 2026);
    }

    @Test
    void nothingIsAggregatedWithoutEnabledConfigs() {
        when(configService.findAllEnabled()).thenReturn(Collections.emptyList());

        job(clockAt("2026-11-26T03:00:00Z"), DatadisSyncWindows.DEFAULT).run();

        verifyNoMoreInteractions(service);
    }

    private DatadisProductionMonthlyAggregationJob job(Clock clock, DatadisSyncWindows windows) {
        return new DatadisProductionMonthlyAggregationJob(service, clock, configService, windows, zoneResolver(MADRID));
    }
}
