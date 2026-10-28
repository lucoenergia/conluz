package org.lucoenergia.conluz.infrastructure.price.sync;

import org.lucoenergia.conluz.domain.price.sync.SyncDailyPricesService;
import org.lucoenergia.conluz.infrastructure.price.OmieConfig;
import org.lucoenergia.conluz.infrastructure.shared.job.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
public class SyncDailyPricesJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncDailyPricesJob.class);

    private final SyncDailyPricesService syncDailyPricesService;
    private final Clock clock;

    public SyncDailyPricesJob(SyncDailyPricesService syncDailyPricesService, Clock clock) {
        this.syncDailyPricesService = syncDailyPricesService;
        this.clock = clock;
    }

    /**
     * This method is executed based on a cron expression to retrieve OMIE prices daily.
     * It synchronises the last week of market days, today included, in OMIE's time zone whatever zone the
     * application is configured with.
     */
    @Override
    @Scheduled(cron = "0 0 6 * * *")
    public void run() {
        LOGGER.info("OMIE prices daily retrieval started...");

        LocalDate today = LocalDate.now(clock.withZone(OmieConfig.OMIE_ZONE));

        syncDailyPricesService.syncDailyPricesByDays(today.minusDays(7), today.plusDays(1));

        LOGGER.info("...finished OMIE prices daily retrieval.");
    }
}
