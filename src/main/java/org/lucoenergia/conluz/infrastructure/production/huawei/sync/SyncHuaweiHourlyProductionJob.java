package org.lucoenergia.conluz.infrastructure.production.huawei.sync;

import org.lucoenergia.conluz.domain.production.huawei.sync.SyncHuaweiProductionService;
import org.lucoenergia.conluz.infrastructure.shared.job.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SyncHuaweiHourlyProductionJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncHuaweiHourlyProductionJob.class);

    private final SyncHuaweiProductionService syncHuaweiProductionService;

    public SyncHuaweiHourlyProductionJob(SyncHuaweiProductionService syncHuaweiProductionService) {
        this.syncHuaweiProductionService = syncHuaweiProductionService;
    }

    /**
     * This method starts the Huawei hourly production sync process every hour at 5 minutes past the hour.
     */
    @Override
    @Scheduled(cron = "0 5 * * * *")
    public void run() {
        LOGGER.info("Huawei hourly production sync started...");

        syncHuaweiProductionService.syncHourlyProduction();

        LOGGER.info("...finished Huawei hourly sync.");
    }
}
