package org.lucoenergia.conluz.infrastructure.datadis.sync;

import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.GetDatadisConfigurationService;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncService;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncWindow;
import org.lucoenergia.conluz.infrastructure.shared.job.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class DatadisSyncDailyJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisSyncDailyJob.class);

    private final DatadisSyncService datadisSyncService;
    private final GetDatadisConfigurationService getDatadisConfigurationService;
    private final DatadisSyncWindow syncWindow;

    public DatadisSyncDailyJob(DatadisSyncService datadisSyncService,
                               GetDatadisConfigurationService getDatadisConfigurationService,
                               DatadisSyncWindow syncWindow) {
        this.datadisSyncService = datadisSyncService;
        this.getDatadisConfigurationService = getDatadisConfigurationService;
        this.syncWindow = syncWindow;
    }

    @Override
    @Scheduled(cron = "0 0 4 * * ?")
    public void run() {
        List<DatadisConfig> enabledConfigs = getDatadisConfigurationService.findAllEnabled();
        if (enabledConfigs.isEmpty()) {
            LOGGER.info("No enabled Datadis configs found. Skipping daily sync.");
            return;
        }

        LOGGER.info("Datadis daily sync started for {} communities...", enabledConfigs.size());

        LocalDate today = LocalDate.now();
        LocalDate firstDay = syncWindow.firstDay(today);

        for (DatadisConfig config : enabledConfigs) {
            LOGGER.info("Syncing community {}", config.getCommunityId());
            try {
                datadisSyncService.synchronize(config.getCommunityId(), firstDay, today);
            } catch (Exception e) {
                LOGGER.error("Failed to sync community {}", config.getCommunityId(), e);
            }
        }

        LOGGER.info("...finished Datadis daily sync.");
    }
}
