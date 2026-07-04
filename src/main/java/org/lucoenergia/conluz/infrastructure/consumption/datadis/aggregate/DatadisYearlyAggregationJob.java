package org.lucoenergia.conluz.infrastructure.consumption.datadis.aggregate;

import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisYearlyAggregationService;
import org.lucoenergia.conluz.infrastructure.shared.job.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class DatadisYearlyAggregationJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisYearlyAggregationJob.class);

    private final DatadisYearlyAggregationService aggregationService;

    public DatadisYearlyAggregationJob(DatadisYearlyAggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    /**
     * Aggregate monthly consumption data into yearly totals at 6:00 AM every day.
     * This job aggregates data for the current year for all supplies.
     *
     * Cron expression breakdown:
     * 0 seconds (at the start of the minute)
     * 0 minutes (at the start of the hour)
     * 6 (at 6 AM)
     * * (every day)
     * * (every month)
     * ? (any day of the week)
     *
     * Rationale:
     * - Runs at 6 AM to avoid conflicts with hourly sync (4 AM) and monthly aggregation (5 AM) jobs.
     * - Runs every day to have update aggregated yearly consumption daily.
     * - By this time, all monthly aggregations for the previous year should be complete.
     */
    @Override
    @Scheduled(cron = "0 0 6 * * ?")
    public void run() {
        LOGGER.info("Datadis yearly aggregation started...");

        LocalDate today = LocalDate.now();
        int year = today.getYear();

        LOGGER.info("Aggregating data for year: {}", year);
        aggregationService.aggregateYearlyConsumptions(year);

        LOGGER.info("...finished Datadis yearly aggregation.");
    }
}
