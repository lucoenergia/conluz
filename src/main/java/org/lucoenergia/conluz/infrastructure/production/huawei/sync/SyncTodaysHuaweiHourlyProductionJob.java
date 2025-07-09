package org.lucoenergia.conluz.infrastructure.production.huawei.sync;

import org.lucoenergia.conluz.domain.production.huawei.sync.SyncHuaweiProductionService;
import org.lucoenergia.conluz.infrastructure.shared.job.Job;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class SyncTodaysHuaweiHourlyProductionJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncTodaysHuaweiHourlyProductionJob.class);

    private final SyncHuaweiProductionService syncHuaweiProductionService;
    private final DateConverter dateConverter;

    public SyncTodaysHuaweiHourlyProductionJob(SyncHuaweiProductionService syncHuaweiProductionService, DateConverter dateConverter) {
        this.syncHuaweiProductionService = syncHuaweiProductionService;
        this.dateConverter = dateConverter;
    }

    /**
     * This method starts the Huawei hourly production sync process every hour at 5 minutes past the hour.
     */
    @Override
    @Scheduled(cron = "0 5 6-22 * * *")
    public void run() {
        LOGGER.info("Huawei hourly production sync started...");

        // Calculate date interval to get data
        OffsetDateTime today = dateConverter.now();

        syncHuaweiProductionService.syncHourlyProduction(today, today);

        LOGGER.info("...finished Huawei hourly sync.");
    }
}
