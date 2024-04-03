package org.lucoenergia.conluz.infrastructure.price.sync;

import org.lucoenergia.conluz.domain.price.sync.SyncDailyPricesService;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class SyncDailyPricesJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncDailyPricesJob.class);

    private final TimeConfiguration timeConfiguration;
    private final SyncDailyPricesService syncDailyPricesService;

    public SyncDailyPricesJob(TimeConfiguration timeConfiguration, SyncDailyPricesService syncDailyPricesService) {
        this.timeConfiguration = timeConfiguration;
        this.syncDailyPricesService = syncDailyPricesService;
    }

    /**
     * Aggregate instant Shelly consumptions by hour every hour.
     * The first field is for seconds. '0' here means at the beginning of the minute.
     * The second field is for the minute field. '0' here means the cron job gets executed every time the minute is '0', or, in other words, at the beginning of each hour.
     * The third field is for the hour.
     * The fourth field is for the day of the month. A '*' means "every day".
     * The fifth field is for the month. A '*' means "every month".
     * The sixth and final field is for the day of the week. A '*' means "every day of the week".
     */
    @Scheduled(cron = "0 6 * * * *")
    public void everyFiveSeconds() {
        LOGGER.info("OMIE prices daily retrieval started...");

        OffsetDateTime today = timeConfiguration.now();

        syncDailyPricesService.syncDailyPrices(today);

        LOGGER.info("...finished OMIE prices daily retrieval.");
    }

}
