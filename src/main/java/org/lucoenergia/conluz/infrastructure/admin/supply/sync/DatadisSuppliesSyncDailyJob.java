package org.lucoenergia.conluz.infrastructure.admin.supply.sync;

import org.lucoenergia.conluz.domain.admin.supply.sync.DatadisSuppliesSyncService;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.GetDatadisConfigurationService;
import org.lucoenergia.conluz.infrastructure.shared.job.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DatadisSuppliesSyncDailyJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisSuppliesSyncDailyJob.class);

    private final DatadisSuppliesSyncService datadisSuppliesSyncService;
    private final GetDatadisConfigurationService getDatadisConfigurationService;

    public DatadisSuppliesSyncDailyJob(DatadisSuppliesSyncService datadisSuppliesSyncService,
                                       GetDatadisConfigurationService getDatadisConfigurationService) {
        this.datadisSuppliesSyncService = datadisSuppliesSyncService;
        this.getDatadisConfigurationService = getDatadisConfigurationService;
    }

    /**
     * Synchronize supplies from datadis at 2:00 AM every day, for every community with an enabled Datadis
     * configuration. A community that fails is logged and does not stop the others.
     * 0 seconds (at the start of the minute)
     * 0 minutes (at the start of the hour)
     * 2 (at 2 AM)
     * * (every day)
     * * (every month)
     * ? (any day of the week)
     */
    @Override
    @Scheduled(cron = "0 0 2 * * ?")
    public void run() {
        List<DatadisConfig> enabledConfigs = getDatadisConfigurationService.findAllEnabled();
        if (enabledConfigs.isEmpty()) {
            LOGGER.info("No enabled Datadis configs found. Skipping Datadis supplies daily sync.");
            return;
        }

        LOGGER.info("Datadis supplies daily sync started for {} communities...", enabledConfigs.size());

        for (DatadisConfig config : enabledConfigs) {
            try {
                datadisSuppliesSyncService.synchronizeSupplies(config.getCommunityId());
            } catch (Exception e) {
                LOGGER.error("Datadis supplies sync failed for community {}", config.getCommunityId(), e);
            }
        }

        LOGGER.info("...finished Datadis supplies daily sync.");
    }
}
