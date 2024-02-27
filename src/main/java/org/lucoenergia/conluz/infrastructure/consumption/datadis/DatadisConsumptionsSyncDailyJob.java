package org.lucoenergia.conluz.infrastructure.consumption.datadis;

import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumptionSyncService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DatadisConsumptionsSyncDailyJob {

    private final DatadisConsumptionSyncService datadisConsumptionSyncService;

    public DatadisConsumptionsSyncDailyJob(DatadisConsumptionSyncService datadisConsumptionSyncService) {
        this.datadisConsumptionSyncService = datadisConsumptionSyncService;
    }

    /**
     * Synchronize consumptions from datadis.es at 4:00 AM every day.
     * 0 seconds (at the start of the minute)
     * 0 minutes (at the start of the hour)
     * 4 (at 4 AM)
     * * (every day)
     * * (every month)
     * ? (any day of the week)
     */
    @Scheduled(cron = "0 0 4 * * ?")
    public void everyFiveSeconds() {
        datadisConsumptionSyncService.synchronizeConsumptions();
    }
}
