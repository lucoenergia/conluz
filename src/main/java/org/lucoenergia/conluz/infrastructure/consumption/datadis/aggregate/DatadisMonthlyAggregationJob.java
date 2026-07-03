package org.lucoenergia.conluz.infrastructure.consumption.datadis.aggregate;

import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisMonthlyAggregationService;
import org.lucoenergia.conluz.infrastructure.shared.job.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Month;

@Component
public class DatadisMonthlyAggregationJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisMonthlyAggregationJob.class);

    private final DatadisMonthlyAggregationService aggregationService;

    public DatadisMonthlyAggregationJob(DatadisMonthlyAggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    /**
     * Aggregate hourly consumption data into monthly totals at 5:00 AM every day.
     * This job aggregates data for the current month for all supplies.
     *
     * Cron expression breakdown:
     * 0 seconds (at the start of the minute)
     * 0 minutes (at the start of the hour)
     * 5 (at 5 AM)
     * * (every day)
     * * (every month)
     * ? (any day of the week)
     *
     * Rationale:
     * - Runs at 5 AM to avoid conflicts with hourly sync job (4 AM)
     * - Runs every day to have updated data for the month daily
     * - By this time, the hourly sync job should have synced all data from previous month
     */
    @Override
    @Scheduled(cron = "0 0 5 * * ?")
    public void run() {
        LOGGER.info("Datadis monthly aggregation started...");

        LocalDate today = LocalDate.now();
        Month month = today.getMonth();
        int year = today.getYear();

        LOGGER.info("Aggregating data for month: {}, year: {}", month, year);
        aggregationService.aggregateMonthlyConsumptions(month, year);

        LOGGER.info("...finished Datadis monthly aggregation.");
    }
}
