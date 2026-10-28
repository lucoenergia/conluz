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
     * Runs once a day at 21:00 in OMIE's time zone, whatever zone the JVM runs in, after OMIE has published
     * tomorrow's prices (around 13:00). It synchronises the last seven market days, today and tomorrow (PRC-008).
     */
    @Override
    @Scheduled(cron = "0 0 21 * * *", zone = "Europe/Madrid")
    public void run() {
        LOGGER.info("OMIE prices daily retrieval started...");

        LocalDate today = LocalDate.now(clock.withZone(OmieConfig.OMIE_ZONE));

        syncDailyPricesService.syncDailyPricesByDays(today.minusDays(7), today.plusDays(2));

        LOGGER.info("...finished OMIE prices daily retrieval.");
    }
}
