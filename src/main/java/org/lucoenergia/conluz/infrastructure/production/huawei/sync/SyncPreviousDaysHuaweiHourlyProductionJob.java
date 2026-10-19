package org.lucoenergia.conluz.infrastructure.production.huawei.sync;

import org.lucoenergia.conluz.domain.production.huawei.config.GetHuaweiConfigurationService;
import org.lucoenergia.conluz.domain.production.huawei.sync.HuaweiSyncWindow;
import org.lucoenergia.conluz.domain.production.huawei.sync.SyncHuaweiProductionService;
import org.lucoenergia.conluz.infrastructure.shared.job.Job;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;

@Component
public class SyncPreviousDaysHuaweiHourlyProductionJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncPreviousDaysHuaweiHourlyProductionJob.class);

    private final SyncHuaweiProductionService syncHuaweiProductionService;
    private final Clock clock;
    private final TimeConfiguration timeConfiguration;
    private final GetHuaweiConfigurationService getHuaweiConfigurationService;
    private final HuaweiSyncWindow syncWindow;

    public SyncPreviousDaysHuaweiHourlyProductionJob(SyncHuaweiProductionService syncHuaweiProductionService,
            Clock clock, TimeConfiguration timeConfiguration,
            GetHuaweiConfigurationService getHuaweiConfigurationService,
            HuaweiSyncWindow syncWindow) {
        this.syncHuaweiProductionService = syncHuaweiProductionService;
        this.clock = clock;
        this.timeConfiguration = timeConfiguration;
        this.getHuaweiConfigurationService = getHuaweiConfigurationService;
        this.syncWindow = syncWindow;
    }

    /**
     * This method starts the Huawei hourly production sync process every hour at 5 minutes past the hour.
     */
    @Override
    @Scheduled(cron = "0 0 1 * * *")
    public void run() {
        if (getHuaweiConfigurationService.isDisabled()) {
            LOGGER.debug("Huawei configuration is disabled. Skipping previous days hourly production sync.");
            return;
        }
        LOGGER.info("Huawei hourly production sync started...");

        // Calculate date interval to get data: from the first day of the window through yesterday
        OffsetDateTime today = OffsetDateTime.ofInstant(clock.instant(), timeConfiguration.getZoneId());
        OffsetDateTime firstDay = today.minus(syncWindow.lookback());

        syncHuaweiProductionService.syncHourlyProduction(firstDay, today.minusDays(1));

        LOGGER.info("...finished Huawei hourly sync.");
    }
}
