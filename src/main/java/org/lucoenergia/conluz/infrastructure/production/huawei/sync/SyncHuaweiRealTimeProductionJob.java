package org.lucoenergia.conluz.infrastructure.production.huawei.sync;

import org.lucoenergia.conluz.domain.production.huawei.config.GetHuaweiConfigurationService;
import org.lucoenergia.conluz.domain.production.huawei.sync.SyncHuaweiProductionService;
import org.lucoenergia.conluz.infrastructure.shared.job.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SyncHuaweiRealTimeProductionJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncHuaweiRealTimeProductionJob.class);

    private final SyncHuaweiProductionService syncHuaweiProductionService;
    private final GetHuaweiConfigurationService getHuaweiConfigurationService;

    public SyncHuaweiRealTimeProductionJob(SyncHuaweiProductionService syncHuaweiProductionService,
            GetHuaweiConfigurationService getHuaweiConfigurationService) {
        this.syncHuaweiProductionService = syncHuaweiProductionService;
        this.getHuaweiConfigurationService = getHuaweiConfigurationService;
    }

    /**
     * This method is a scheduled task that is executed every 5 minutes.
     * It synchronizes the real-time production data from Huawei energy stations.
     */
    @Override
    @Scheduled(cron = "0 */5 * * * *")
    public void run() {
        if (getHuaweiConfigurationService.isDisabled()) {
            LOGGER.debug("Huawei configuration is disabled. Skipping real-time production sync.");
            return;
        }
        LOGGER.debug("Huawei real-time production sync started...");

        syncHuaweiProductionService.syncRealTimeProduction();

        LOGGER.debug("...finished Huawei real-time sync.");
    }
}
