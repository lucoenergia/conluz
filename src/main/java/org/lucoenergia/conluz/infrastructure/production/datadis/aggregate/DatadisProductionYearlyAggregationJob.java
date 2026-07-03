package org.lucoenergia.conluz.infrastructure.production.datadis.aggregate;

import org.lucoenergia.conluz.domain.production.datadis.aggregate.DatadisProductionYearlyAggregationService;
import org.lucoenergia.conluz.infrastructure.shared.job.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class DatadisProductionYearlyAggregationJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisProductionYearlyAggregationJob.class);

    private final DatadisProductionYearlyAggregationService aggregationService;

    public DatadisProductionYearlyAggregationJob(DatadisProductionYearlyAggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    /**
     * Aggregate monthly production data into yearly totals at 6:00 AM every day.
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
     * - Runs at 6 AM to avoid conflicts with the hourly sync (4 AM) and monthly aggregation
     *   (5 AM) jobs. The yearly aggregation reads the monthly measurement, so it must run after
     *   the monthly aggregation has populated it.
     * - Runs every day to keep the aggregated yearly production up to date daily.
     */
    @Override
    @Scheduled(cron = "0 0 6 * * ?")
    public void run() {
        LOGGER.info("Datadis production yearly aggregation started...");

        LocalDate today = LocalDate.now();
        int year = today.getYear();

        LOGGER.info("Aggregating production data for year: {}", year);
        aggregationService.aggregateYearlyProductions(year);

        LOGGER.info("...finished Datadis production yearly aggregation.");
    }
}
