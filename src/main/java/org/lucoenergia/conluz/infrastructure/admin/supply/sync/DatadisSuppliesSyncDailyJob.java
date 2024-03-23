package org.lucoenergia.conluz.infrastructure.admin.supply.sync;

import org.lucoenergia.conluz.domain.admin.supply.sync.DatadisSuppliesSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DatadisSuppliesSyncDailyJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisSuppliesSyncDailyJob.class);

    private final DatadisSuppliesSyncService datadisSuppliesSyncService;

    public DatadisSuppliesSyncDailyJob(DatadisSuppliesSyncService datadisSuppliesSyncService) {
        this.datadisSuppliesSyncService = datadisSuppliesSyncService;
    }

    /**
     * Synchronize supplies from datadis.es at 2:00 AM every day.
     * 0 seconds (at the start of the minute)
     * 0 minutes (at the start of the hour)
     * 2 (at 2 AM)
     * * (every day)
     * * (every month)
     * ? (any day of the week)
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void everyFiveSeconds() {
        LOGGER.info("Datadis supplies daily sync started...");
        datadisSuppliesSyncService.synchronizeSupplies();
        LOGGER.info("...finished Datadis supplies daily sync.");
    }
}
