package org.lucoenergia.conluz.infrastructure.price.sync;

import org.lucoenergia.conluz.domain.price.sync.SyncDailyPricesService;
import org.lucoenergia.conluz.infrastructure.shared.job.Job;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;

@Component
public class SyncDailyPricesJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncDailyPricesJob.class);

    private final TimeConfiguration timeConfiguration;
    private final SyncDailyPricesService syncDailyPricesService;
    private final Clock clock;

    public SyncDailyPricesJob(TimeConfiguration timeConfiguration, SyncDailyPricesService syncDailyPricesService,
                              Clock clock) {
        this.timeConfiguration = timeConfiguration;
        this.syncDailyPricesService = syncDailyPricesService;
        this.clock = clock;
    }

    /**
     * This method is executed based on a cron expression to retrieve OMIE prices daily.
     * It retrieves the current date and time and passes it to synchronize the daily prices.
     */
    @Override
    @Scheduled(cron = "0 0 6 * * *")
    public void run() {
        LOGGER.info("OMIE prices daily retrieval started...");

        OffsetDateTime today = OffsetDateTime.ofInstant(clock.instant(), timeConfiguration.getZoneId());
        OffsetDateTime todayMinusOneWeek = today.minusWeeks(1);

        syncDailyPricesService.syncDailyPricesByDateInterval(todayMinusOneWeek, today);

        LOGGER.info("...finished OMIE prices daily retrieval.");
    }
}
