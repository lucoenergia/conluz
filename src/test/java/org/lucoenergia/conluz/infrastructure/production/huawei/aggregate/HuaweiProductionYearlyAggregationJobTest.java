package org.lucoenergia.conluz.infrastructure.production.huawei.aggregate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionYearlyAggregationService;
import org.lucoenergia.conluz.domain.production.huawei.config.GetHuaweiConfigurationService;
import org.lucoenergia.conluz.domain.production.huawei.sync.HuaweiSyncWindow;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.time.Clock;
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

class HuaweiProductionYearlyAggregationJobTest {

    private static final UUID PLANT_A = UUID.randomUUID();
    private static final UUID PLANT_B = UUID.randomUUID();

    private final HuaweiProductionYearlyAggregationService service =
            Mockito.mock(HuaweiProductionYearlyAggregationService.class);
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
        job(clockAt("2026-10-19T01:00:00Z")).run();

        verify(service).aggregatePlantYearlyProduction(PLANT_A, 2026);
        verify(service).aggregatePlantYearlyProduction(PLANT_B, 2026);
        verifyNoMoreInteractions(service);
    }

    @Test
    void aConfigWithoutPlantIsSkipped() {
        when(configService.findAllEnabled()).thenReturn(List.of(enabledConfig(null), enabledConfig(PLANT_B)));

        job(clockAt("2026-10-19T01:00:00Z")).run();

        verify(service).aggregatePlantYearlyProduction(PLANT_B, 2026);
        verifyNoMoreInteractions(service);
    }

    @Test
    void aFailingPlantDoesNotStopTheOthers() {
        doThrow(new RuntimeException("boom")).when(service).aggregatePlantYearlyProduction(eq(PLANT_A), anyInt());

        job(clockAt("2026-10-19T01:00:00Z")).run();

        verify(service).aggregatePlantYearlyProduction(PLANT_B, 2026);
    }

    @Test
    void nothingIsAggregatedWithoutEnabledConfigs() {
        when(configService.findAllEnabled()).thenReturn(Collections.emptyList());

        job(clockAt("2026-10-19T01:00:00Z")).run();

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("HPA-003 on 1 January, the previous and the current year are re-aggregated for the plant of every enabled config")
    void onTheFirstOfJanuaryBothYearsAreReAggregated() {
        // 2027-01-01 at 03:00 in Madrid: the window starts on 2026-12-25.
        job(clockAt("2027-01-01T02:00:00Z")).run();

        for (UUID plant : List.of(PLANT_A, PLANT_B)) {
            InOrder inOrder = inOrder(service);
            inOrder.verify(service).aggregatePlantYearlyProduction(plant, 2026);
            inOrder.verify(service).aggregatePlantYearlyProduction(plant, 2027);
        }
        verifyNoMoreInteractions(service);
    }

    @Test
    @ResourceLock(Resources.TIME_ZONE)
    @DisplayName("HPA-003 the current year is resolved in the configured zone, not the JVM default (00:30 on 1 January in Madrid)")
    void theCurrentYearIsResolvedInTheConfiguredZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        // 2027-01-01T00:30 in Madrid, still 2026-12-31 in UTC.
        job(clockAt("2026-12-31T23:30:00Z")).run();

        verify(service).aggregatePlantYearlyProduction(PLANT_A, 2027);
        verify(service).aggregatePlantYearlyProduction(PLANT_B, 2027);
    }

    @Test
    @DisplayName("HPA-003 a year the window does not overlap is not re-aggregated")
    void aYearOutsideTheWindowIsNotReAggregated() {
        // 2027-01-08 minus 7 days is 2027-01-01.
        job(clockAt("2027-01-08T02:00:00Z")).run();

        verify(service).aggregatePlantYearlyProduction(PLANT_A, 2027);
        verify(service, never()).aggregatePlantYearlyProduction(any(UUID.class), eq(2026));
    }

    @Test
    @DisplayName("HPA-003 the years follow the sync window, with no second value to change")
    void theYearsFollowTheSyncWindow() {
        job(clockAt("2027-01-08T02:00:00Z"), new HuaweiSyncWindow(Period.ofDays(8))).run();

        verify(service).aggregatePlantYearlyProduction(PLANT_A, 2026);
        verify(service).aggregatePlantYearlyProduction(PLANT_A, 2027);
    }

    private HuaweiProductionYearlyAggregationJob job(Clock clock, HuaweiSyncWindow window) {
        return new HuaweiProductionYearlyAggregationJob(service, clock, configService, window, zoneResolver(MADRID));
    }

    private HuaweiProductionYearlyAggregationJob job(Clock clock) {
        return new HuaweiProductionYearlyAggregationJob(service, clock, configService, HuaweiSyncWindow.DEFAULT,
                zoneResolver(MADRID));
    }
}
