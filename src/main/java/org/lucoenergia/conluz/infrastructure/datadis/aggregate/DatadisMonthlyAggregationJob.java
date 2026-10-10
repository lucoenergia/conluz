package org.lucoenergia.conluz.infrastructure.datadis.aggregate;

import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisMonthlyAggregationService;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.GetDatadisConfigurationService;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncWindow;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.shared.job.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Component
public class DatadisMonthlyAggregationJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisMonthlyAggregationJob.class);

    private final DatadisMonthlyAggregationService aggregationService;
    private final Clock clock;
    private final GetDatadisConfigurationService getDatadisConfigurationService;
    private final DatadisSyncWindow syncWindow;
    private final ZoneResolver zoneResolver;

    public DatadisMonthlyAggregationJob(DatadisMonthlyAggregationService aggregationService,
                                        Clock clock,
                                        GetDatadisConfigurationService getDatadisConfigurationService,
                                        DatadisSyncWindow syncWindow,
                                        ZoneResolver zoneResolver) {
        this.aggregationService = aggregationService;
        this.clock = clock;
        this.getDatadisConfigurationService = getDatadisConfigurationService;
        this.syncWindow = syncWindow;
        this.zoneResolver = zoneResolver;
    }

    /**
     * Aggregate hourly consumption data into monthly totals at 5:00 AM every day.
     * For every community with Datadis enabled, this job re-aggregates every month of the window the
     * daily sync re-reads ({@link DatadisSyncWindow#months(LocalDate)}), not only the current one:
     * Datadis publishes and revises hourly records after a month has closed, and the sync writes
     * those revisions to the hourly series, so a closed month's total would otherwise stay frozen at
     * whatever was aggregated on its last day.
     *
     * The current date is resolved in the community's zone, so the window follows the community's
     * calendar rather than the JVM default zone.
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
     * - By this time, the hourly sync job should have re-synced every month of the window
     */
    @Override
    @Scheduled(cron = "0 0 5 * * ?")
    public void run() {
        List<DatadisConfig> enabledConfigs = getDatadisConfigurationService.findAllEnabled();
        if (enabledConfigs.isEmpty()) {
            LOGGER.info("No enabled Datadis configs found. Skipping Datadis monthly aggregation.");
            return;
        }

        LOGGER.info("Datadis monthly aggregation started for {} communities...", enabledConfigs.size());

        for (DatadisConfig config : enabledConfigs) {
            aggregateCommunity(config);
        }

        LOGGER.info("...finished Datadis monthly aggregation.");
    }

    private void aggregateCommunity(DatadisConfig config) {
        try {
            LocalDate today = LocalDate.now(clock.withZone(
                    zoneResolver.resolveZoneIdForCommunity(config.getCommunityId())));
            for (YearMonth month : syncWindow.months(today)) {
                LOGGER.info("Aggregating data for community: {}, month: {}", config.getCommunityId(), month);
                aggregationService.aggregateMonthlyConsumptions(config.getCommunityId(), month.getMonth(),
                        month.getYear());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to aggregate monthly consumption for community {}", config.getCommunityId(), e);
        }
    }
}
