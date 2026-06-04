package org.lucoenergia.conluz.infrastructure.consumption.datadis.sync;

import org.lucoenergia.conluz.domain.consumption.datadis.config.DatadisConfig;
import org.lucoenergia.conluz.domain.consumption.datadis.config.GetDatadisConfigurationService;
import org.lucoenergia.conluz.domain.consumption.datadis.sync.DatadisConsumptionSyncService;
import org.lucoenergia.conluz.infrastructure.shared.job.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class DatadisConsumptionsSyncDailyJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisConsumptionsSyncDailyJob.class);

    private final DatadisConsumptionSyncService datadisConsumptionSyncService;
    private final GetDatadisConfigurationService getDatadisConfigurationService;

    public DatadisConsumptionsSyncDailyJob(DatadisConsumptionSyncService datadisConsumptionSyncService,
                                           GetDatadisConfigurationService getDatadisConfigurationService) {
        this.datadisConsumptionSyncService = datadisConsumptionSyncService;
        this.getDatadisConfigurationService = getDatadisConfigurationService;
    }

    @Override
    @Scheduled(cron = "0 0 4 * * ?")
    public void run() {
        List<DatadisConfig> enabledConfigs = getDatadisConfigurationService.getEnabledDatadisConfigurations();
        if (enabledConfigs.isEmpty()) {
            LOGGER.info("No enabled Datadis configs found. Skipping daily sync.");
            return;
        }

        LOGGER.info("Datadis consumption daily sync started for {} communities...", enabledConfigs.size());

        LocalDate today = LocalDate.now();
        LocalDate oneYearAgo = today.minusYears(1).withDayOfMonth(1);

        for (DatadisConfig config : enabledConfigs) {
            LOGGER.info("Syncing community {}", config.getCommunityId());
            try {
                datadisConsumptionSyncService.synchronizeConsumptions(config.getCommunityId(), oneYearAgo, today);
            } catch (Exception e) {
                LOGGER.error("Failed to sync community {}", config.getCommunityId(), e);
            }
        }

        LOGGER.info("...finished Datadis consumption daily sync.");
    }
}
