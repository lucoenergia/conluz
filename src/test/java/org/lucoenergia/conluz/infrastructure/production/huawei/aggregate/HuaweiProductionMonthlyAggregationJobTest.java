package org.lucoenergia.conluz.infrastructure.production.huawei.aggregate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionMonthlyAggregationService;
import org.lucoenergia.conluz.domain.production.huawei.config.GetHuaweiConfigurationService;
import org.lucoenergia.conluz.domain.production.huawei.sync.HuaweiSyncWindow;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Month;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.lucoenergia.conluz.infrastructure.production.huawei.HuaweiJobTestSupport.MADRID;
import static org.lucoenergia.conluz.infrastructure.production.huawei.HuaweiJobTestSupport.clockAt;
import static org.lucoenergia.conluz.infrastructure.production.huawei.HuaweiJobTestSupport.enabledConfig;
import static org.lucoenergia.conluz.infrastructure.production.huawei.HuaweiJobTestSupport.zoneResolver;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

    @BeforeEach
    void setUp() {
        when(configService.findAllEnabled()).thenReturn(List.of(enabledConfig(PLANT_A), enabledConfig(PLANT_B)));
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

    private HuaweiProductionMonthlyAggregationJob job(Clock clock) {
        return new HuaweiProductionMonthlyAggregationJob(service, clock, configService, HuaweiSyncWindow.DEFAULT,
                zoneResolver(MADRID));
    }
}
