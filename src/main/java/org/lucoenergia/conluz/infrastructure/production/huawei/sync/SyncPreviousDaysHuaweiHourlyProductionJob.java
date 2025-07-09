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
public class SyncPreviousDaysHuaweiHourlyProductionJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncPreviousDaysHuaweiHourlyProductionJob.class);

    private final SyncHuaweiProductionService syncHuaweiProductionService;
    private final DateConverter dateConverter;

    public SyncPreviousDaysHuaweiHourlyProductionJob(SyncHuaweiProductionService syncHuaweiProductionService, DateConverter dateConverter) {
        this.syncHuaweiProductionService = syncHuaweiProductionService;
        this.dateConverter = dateConverter;
    }

    /**
     * This method starts the Huawei hourly production sync process every hour at 5 minutes past the hour.
     */
    @Override
    @Scheduled(cron = "0 0 1 * * *")
    public void run() {
        LOGGER.info("Huawei hourly production sync started...");

        // Calculate date interval to get data
        OffsetDateTime today = dateConverter.now();
        OffsetDateTime todayMinusOneWeek = today.minusWeeks(1);

        syncHuaweiProductionService.syncHourlyProduction(todayMinusOneWeek, today.minusDays(1));

        LOGGER.info("...finished Huawei hourly sync.");
    }
}
