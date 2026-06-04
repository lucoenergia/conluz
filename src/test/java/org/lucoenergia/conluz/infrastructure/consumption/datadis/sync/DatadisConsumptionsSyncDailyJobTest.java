package org.lucoenergia.conluz.infrastructure.consumption.datadis.sync;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.datadis.config.DatadisConfig;
import org.lucoenergia.conluz.domain.consumption.datadis.config.GetDatadisConfigurationService;
import org.lucoenergia.conluz.domain.consumption.datadis.sync.DatadisConsumptionSyncService;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class DatadisConsumptionsSyncDailyJobTest {

    @Test
    void testRun_ShouldCallSynchronizeConsumptionsPerCommunity() {
        DatadisConsumptionSyncService mockSyncService = Mockito.mock(DatadisConsumptionSyncService.class);
        GetDatadisConfigurationService mockConfigService = Mockito.mock(GetDatadisConfigurationService.class);
        UUID communityId = UUID.randomUUID();
        DatadisConfig enabledConfig = new DatadisConfig.Builder()
                .setCommunityId(communityId).setEnabled(Boolean.TRUE)
                .setUsername("u").setPassword("p").build();
        when(mockConfigService.getEnabledDatadisConfigurations()).thenReturn(List.of(enabledConfig));

        DatadisConsumptionsSyncDailyJob job = new DatadisConsumptionsSyncDailyJob(mockSyncService, mockConfigService);

        LocalDate today = LocalDate.now();
        LocalDate oneYearAgo = today.minusYears(1).withDayOfMonth(1);

        job.run();

        verify(mockSyncService, times(1)).synchronizeConsumptions(communityId, oneYearAgo, today);
    }

    @Test
    void testRun_ShouldSkipSyncWhenNoEnabledConfigs() {
        DatadisConsumptionSyncService mockSyncService = Mockito.mock(DatadisConsumptionSyncService.class);
        GetDatadisConfigurationService mockConfigService = Mockito.mock(GetDatadisConfigurationService.class);
        when(mockConfigService.getEnabledDatadisConfigurations()).thenReturn(Collections.emptyList());

        DatadisConsumptionsSyncDailyJob job = new DatadisConsumptionsSyncDailyJob(mockSyncService, mockConfigService);

        job.run();

        verify(mockSyncService, never()).synchronizeConsumptions(any(UUID.class), any(), any());
    }
}
