package org.lucoenergia.conluz.infrastructure.production.huawei.sync;

import org.lucoenergia.conluz.domain.production.huawei.config.GetHuaweiConfigurationService;
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
public class SyncTodaysHuaweiHourlyProductionJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncTodaysHuaweiHourlyProductionJob.class);

    private final SyncHuaweiProductionService syncHuaweiProductionService;
    private final Clock clock;
    private final TimeConfiguration timeConfiguration;
    private final GetHuaweiConfigurationService getHuaweiConfigurationService;

    public SyncTodaysHuaweiHourlyProductionJob(SyncHuaweiProductionService syncHuaweiProductionService,
            Clock clock, TimeConfiguration timeConfiguration,
            GetHuaweiConfigurationService getHuaweiConfigurationService) {
        this.syncHuaweiProductionService = syncHuaweiProductionService;
        this.clock = clock;
        this.timeConfiguration = timeConfiguration;
        this.getHuaweiConfigurationService = getHuaweiConfigurationService;
    }

    /**
     * This method starts the Huawei hourly production sync process every hour at 5 minutes past the hour.
     */
    @Override
    @Scheduled(cron = "0 5 6-22 * * *")
    public void run() {
        if (getHuaweiConfigurationService.isDisabled()) {
            LOGGER.debug("Huawei configuration is disabled. Skipping today's hourly production sync.");
            return;
        }
        LOGGER.info("Huawei hourly production sync started...");

        // Calculate date interval to get data
        OffsetDateTime today = OffsetDateTime.ofInstant(clock.instant(), timeConfiguration.getZoneId());

        syncHuaweiProductionService.syncHourlyProduction(today, today);

        LOGGER.info("...finished Huawei hourly sync.");
    }
}
