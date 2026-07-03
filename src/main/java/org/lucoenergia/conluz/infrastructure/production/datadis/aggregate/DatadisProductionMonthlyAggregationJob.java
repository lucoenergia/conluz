package org.lucoenergia.conluz.infrastructure.production.datadis.aggregate;

import org.lucoenergia.conluz.domain.production.datadis.aggregate.DatadisProductionMonthlyAggregationService;
import org.lucoenergia.conluz.infrastructure.shared.job.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Month;

@Component
public class DatadisProductionMonthlyAggregationJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisProductionMonthlyAggregationJob.class);

    private final DatadisProductionMonthlyAggregationService aggregationService;

    public DatadisProductionMonthlyAggregationJob(DatadisProductionMonthlyAggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    /**
     * Aggregate hourly production data into monthly totals at 5:00 AM every day.
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
     * - Runs at 5 AM to avoid conflicts with the hourly sync job (4 AM), which also writes
     *   the hourly production series consumed here.
     * - Runs every day to have updated data for the month daily.
     */
    @Override
    @Scheduled(cron = "0 0 5 * * ?")
    public void run() {
        LOGGER.info("Datadis production monthly aggregation started...");

        LocalDate today = LocalDate.now();
        Month month = today.getMonth();
        int year = today.getYear();

        LOGGER.info("Aggregating production data for month: {}, year: {}", month, year);
        aggregationService.aggregateMonthlyProductions(month, year);

        LOGGER.info("...finished Datadis production monthly aggregation.");
    }
}
