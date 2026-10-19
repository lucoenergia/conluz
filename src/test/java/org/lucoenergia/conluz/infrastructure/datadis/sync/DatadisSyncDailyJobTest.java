package org.lucoenergia.conluz.infrastructure.datadis.sync;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.GetDatadisConfigurationService;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncService;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncWindow;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class DatadisSyncDailyJobTest {

    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void testRun_ShouldCallSynchronizeConsumptionsPerCommunity() {
        DatadisSyncService mockSyncService = Mockito.mock(DatadisSyncService.class);
        GetDatadisConfigurationService mockConfigService = Mockito.mock(GetDatadisConfigurationService.class);
        UUID communityId = UUID.randomUUID();
        DatadisConfig enabledConfig = new DatadisConfig.Builder()
                .setCommunityId(communityId).setEnabled(Boolean.TRUE)
                .setUsername("u").setPassword("p").build();
        when(mockConfigService.findAllEnabled()).thenReturn(List.of(enabledConfig));

        DatadisSyncDailyJob job = new DatadisSyncDailyJob(mockSyncService, mockConfigService, DatadisSyncWindow.DEFAULT,
                CLOCK);

        // Today as the job reads it: in the JVM's default zone
        LocalDate today = LocalDate.ofInstant(NOW, ZoneId.systemDefault());
        LocalDate oneYearAgo = today.minusYears(1).withDayOfMonth(1);

        job.run();

        verify(mockSyncService, times(1)).synchronize(communityId, oneYearAgo, today);
    }

    @Test
    void testRun_ShouldSkipSyncWhenNoEnabledConfigs() {
        DatadisSyncService mockSyncService = Mockito.mock(DatadisSyncService.class);
        GetDatadisConfigurationService mockConfigService = Mockito.mock(GetDatadisConfigurationService.class);
        when(mockConfigService.findAllEnabled()).thenReturn(Collections.emptyList());

        DatadisSyncDailyJob job = new DatadisSyncDailyJob(mockSyncService, mockConfigService, DatadisSyncWindow.DEFAULT,
                CLOCK);

        job.run();

        verify(mockSyncService, never()).synchronize(any(UUID.class), any(), any());
    }
}
