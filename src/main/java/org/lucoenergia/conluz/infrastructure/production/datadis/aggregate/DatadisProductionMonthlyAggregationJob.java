package org.lucoenergia.conluz.infrastructure.production.datadis.aggregate;

import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.GetDatadisConfigurationService;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncWindow;
import org.lucoenergia.conluz.domain.production.datadis.aggregate.DatadisProductionMonthlyAggregationService;
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
public class DatadisProductionMonthlyAggregationJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisProductionMonthlyAggregationJob.class);

    private final DatadisProductionMonthlyAggregationService aggregationService;
    private final Clock clock;
    private final GetDatadisConfigurationService getDatadisConfigurationService;
    private final DatadisSyncWindow syncWindow;
    private final ZoneResolver zoneResolver;

    public DatadisProductionMonthlyAggregationJob(DatadisProductionMonthlyAggregationService aggregationService,
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
     * Aggregate hourly production data into monthly totals at 5:00 AM every day.
     * For every community with Datadis enabled, this job re-aggregates every month of the window the
     * daily sync re-reads ({@link DatadisSyncWindow#months(LocalDate)}), not only the current one:
     * the sync rewrites the hourly production of plant supplies over that whole window, so a closed
     * month's total would otherwise stay frozen at whatever was aggregated on its last day.
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
     * - Runs at 5 AM to avoid conflicts with the hourly sync job (4 AM), which also writes
     *   the hourly production series consumed here.
     * - Runs every day to have updated data for the month daily.
     */
    @Override
    @Scheduled(cron = "0 0 5 * * ?")
    public void run() {
        List<DatadisConfig> enabledConfigs = getDatadisConfigurationService.findAllEnabled();
        if (enabledConfigs.isEmpty()) {
            LOGGER.info("No enabled Datadis configs found. Skipping Datadis production monthly aggregation.");
            return;
        }

        LOGGER.info("Datadis production monthly aggregation started for {} communities...", enabledConfigs.size());

        for (DatadisConfig config : enabledConfigs) {
            aggregateCommunity(config);
        }

        LOGGER.info("...finished Datadis production monthly aggregation.");
    }

    private void aggregateCommunity(DatadisConfig config) {
        try {
            LocalDate today = LocalDate.now(clock.withZone(
                    zoneResolver.resolveZoneIdForCommunity(config.getCommunityId())));
            for (YearMonth month : syncWindow.months(today)) {
                LOGGER.info("Aggregating production data for community: {}, month: {}",
                        config.getCommunityId(), month);
                aggregationService.aggregateMonthlyProductions(config.getCommunityId(), month.getMonth(),
                        month.getYear());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to aggregate monthly production for community {}", config.getCommunityId(), e);
        }
    }
}
