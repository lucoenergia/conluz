package org.lucoenergia.conluz.infrastructure.production.huawei.aggregate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionMonthlyAggregationService;
import org.lucoenergia.conluz.domain.production.huawei.config.GetHuaweiConfigurationService;
import org.lucoenergia.conluz.domain.production.huawei.sync.HuaweiSyncWindow;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Month;
import java.time.Period;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import static org.lucoenergia.conluz.infrastructure.production.huawei.HuaweiJobTestSupport.MADRID;
import static org.lucoenergia.conluz.infrastructure.production.huawei.HuaweiJobTestSupport.clockAt;
import static org.lucoenergia.conluz.infrastructure.production.huawei.HuaweiJobTestSupport.enabledConfig;
import static org.lucoenergia.conluz.infrastructure.production.huawei.HuaweiJobTestSupport.zoneResolver;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class HuaweiProductionMonthlyAggregationJobTest {

    private static final UUID PLANT_A = UUID.randomUUID();
    private static final UUID PLANT_B = UUID.randomUUID();

    private final HuaweiProductionMonthlyAggregationService service =
            Mockito.mock(HuaweiProductionMonthlyAggregationService.class);
    private final GetHuaweiConfigurationService configService = Mockito.mock(GetHuaweiConfigurationService.class);

    private TimeZone originalDefaultZone;

    @BeforeEach
    void setUp() {
        originalDefaultZone = TimeZone.getDefault();
        when(configService.findAllEnabled()).thenReturn(List.of(enabledConfig(PLANT_A), enabledConfig(PLANT_B)));
    }

    @AfterEach
    void restoreDefaultZone() {
        TimeZone.setDefault(originalDefaultZone);
    }

    @Test
    void thePlantOfEveryEnabledConfigIsAggregated() {
        job(clockAt("2026-10-19T00:00:00Z")).run();

        verify(service).aggregatePlantMonthlyProduction(PLANT_A, Month.OCTOBER, 2026);
        verify(service).aggregatePlantMonthlyProduction(PLANT_B, Month.OCTOBER, 2026);
        verifyNoMoreInteractions(service);
    }

    @Test
    void aConfigWithoutPlantIsSkipped() {
        when(configService.findAllEnabled()).thenReturn(List.of(enabledConfig(null), enabledConfig(PLANT_B)));

        job(clockAt("2026-10-19T00:00:00Z")).run();

        verify(service).aggregatePlantMonthlyProduction(PLANT_B, Month.OCTOBER, 2026);
        verifyNoMoreInteractions(service);
    }

    @Test
    void aFailingPlantDoesNotStopTheOthers() {
        doThrow(new RuntimeException("boom"))
                .when(service).aggregatePlantMonthlyProduction(eq(PLANT_A), any(Month.class), anyInt());

        job(clockAt("2026-10-19T00:00:00Z")).run();

        verify(service).aggregatePlantMonthlyProduction(PLANT_B, Month.OCTOBER, 2026);
    }

    @Test
    void nothingIsAggregatedWithoutEnabledConfigs() {
        when(configService.findAllEnabled()).thenReturn(Collections.emptyList());

        job(clockAt("2026-10-19T00:00:00Z")).run();

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("HPA-003 every month the window overlaps is re-aggregated for the plant of every enabled config, and nothing else")
    void everyMonthOfTheWindowIsReAggregatedForEveryPlant() {
        // 2026-10-03 at 02:00 in Madrid: the window starts on 2026-09-26.
        job(clockAt("2026-10-03T00:00:00Z")).run();

        for (UUID plant : List.of(PLANT_A, PLANT_B)) {
            InOrder inOrder = inOrder(service);
            inOrder.verify(service).aggregatePlantMonthlyProduction(plant, Month.SEPTEMBER, 2026);
            inOrder.verify(service).aggregatePlantMonthlyProduction(plant, Month.OCTOBER, 2026);
        }
        verifyNoMoreInteractions(service);
    }

    @Test
    @DisplayName("HPA-003 on the 1st, the previous month is re-aggregated after its last day has been synced")
    void onTheFirstThePreviousMonthIsReAggregated() {
        // 2026-11-01 at 02:00 in Madrid (CET).
        job(clockAt("2026-11-01T01:00:00Z")).run();

        for (UUID plant : List.of(PLANT_A, PLANT_B)) {
            verify(service).aggregatePlantMonthlyProduction(plant, Month.OCTOBER, 2026);
            verify(service).aggregatePlantMonthlyProduction(plant, Month.NOVEMBER, 2026);
        }
        verifyNoMoreInteractions(service);
    }

    @Test
    @ResourceLock(Resources.TIME_ZONE)
    @DisplayName("HPA-003 the current month is resolved in the configured zone, not the JVM default (00:30 on 1 October in Madrid)")
    void theCurrentMonthIsResolvedInTheConfiguredZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        // 2026-10-01T00:30 in Madrid, still 2026-09-30 in UTC.
        job(clockAt("2026-09-30T22:30:00Z")).run();

        verify(service).aggregatePlantMonthlyProduction(PLANT_A, Month.OCTOBER, 2026);
        verify(service).aggregatePlantMonthlyProduction(PLANT_B, Month.OCTOBER, 2026);
        verify(service, never()).aggregatePlantMonthlyProduction(any(UUID.class), eq(Month.AUGUST), anyInt());
    }

    @Test
    @DisplayName("HPA-003 the months follow the sync window, with no second value to change")
    void theMonthsFollowTheSyncWindow() {
        // 2026-10-19 minus 40 days is 2026-09-09.
        job(clockAt("2026-10-19T00:00:00Z"), new HuaweiSyncWindow(Period.ofDays(40))).run();

        verify(service).aggregatePlantMonthlyProduction(PLANT_A, Month.SEPTEMBER, 2026);
        verify(service).aggregatePlantMonthlyProduction(PLANT_A, Month.OCTOBER, 2026);
        verify(service, never()).aggregatePlantMonthlyProduction(PLANT_A, Month.AUGUST, 2026);
    }

    private HuaweiProductionMonthlyAggregationJob job(Clock clock, HuaweiSyncWindow window) {
        return new HuaweiProductionMonthlyAggregationJob(service, clock, configService, window, zoneResolver(MADRID));
    }

    private HuaweiProductionMonthlyAggregationJob job(Clock clock) {
        return new HuaweiProductionMonthlyAggregationJob(service, clock, configService, HuaweiSyncWindow.DEFAULT,
                zoneResolver(MADRID));
    }
}
