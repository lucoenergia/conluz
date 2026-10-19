package org.lucoenergia.conluz.infrastructure.production.huawei.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.huawei.config.GetHuaweiConfigurationService;
import org.lucoenergia.conluz.domain.production.huawei.sync.HuaweiSyncWindow;
import org.lucoenergia.conluz.domain.production.huawei.sync.SyncHuaweiProductionService;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyncPreviousDaysHuaweiHourlyProductionJobTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-19T01:00:00+02:00");

    private final SyncHuaweiProductionService syncService = mock(SyncHuaweiProductionService.class);
    private final TimeConfiguration timeConfiguration = mock(TimeConfiguration.class);
    private final GetHuaweiConfigurationService configService = mock(GetHuaweiConfigurationService.class);

    @BeforeEach
    void inMadrid() {
        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of("Europe/Madrid"));
    }

    @Test
    void theDefaultWindowSyncsTheSevenDaysBeforeToday() {

        job(HuaweiSyncWindow.DEFAULT).run();

        verify(syncService).syncHourlyProduction(
                OffsetDateTime.parse("2026-10-12T01:00:00+02:00"),
                OffsetDateTime.parse("2026-10-18T01:00:00+02:00"));
    }

    @Test
    void theFirstSyncedDayFollowsTheWindow() {

        job(new HuaweiSyncWindow(Period.ofDays(3))).run();

        verify(syncService).syncHourlyProduction(
                OffsetDateTime.parse("2026-10-16T01:00:00+02:00"),
                OffsetDateTime.parse("2026-10-18T01:00:00+02:00"));
    }

    @Test
    void nothingIsSyncedWhenHuaweiIsDisabled() {
        when(configService.isDisabled()).thenReturn(true);

        job(HuaweiSyncWindow.DEFAULT).run();

        verify(syncService, never()).syncHourlyProduction(any(), any());
    }

    private SyncPreviousDaysHuaweiHourlyProductionJob job(HuaweiSyncWindow window) {
        return new SyncPreviousDaysHuaweiHourlyProductionJob(syncService, Clock.fixed(NOW.toInstant(), NOW.getOffset()),
                timeConfiguration, configService, window);
    }
}
