package org.lucoenergia.conluz.infrastructure.production.datadis.aggregate;

import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.GetDatadisConfigurationService;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncWindow;
import org.lucoenergia.conluz.domain.production.datadis.aggregate.DatadisProductionYearlyAggregationService;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.shared.job.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Component
public class DatadisProductionYearlyAggregationJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisProductionYearlyAggregationJob.class);

    private final DatadisProductionYearlyAggregationService aggregationService;
    private final Clock clock;
    private final GetDatadisConfigurationService getDatadisConfigurationService;
    private final DatadisSyncWindow syncWindow;
    private final ZoneResolver zoneResolver;

    public DatadisProductionYearlyAggregationJob(DatadisProductionYearlyAggregationService aggregationService,
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
     * Aggregate monthly production data into yearly totals at 6:00 AM every day.
     * For every community with Datadis enabled, this job re-aggregates every year the window of the
     * daily sync overlaps ({@link DatadisSyncWindow#years(LocalDate)}), not only the current one:
     * the monthly production aggregation rewrites every month of that window, including the months
     * of the previous year it reaches, so that year's total would otherwise stay frozen.
     *
     * The current date is resolved in the community's zone, so the window follows the community's
     * calendar rather than the JVM default zone.
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
        List<DatadisConfig> enabledConfigs = getDatadisConfigurationService.findAllEnabled();
        if (enabledConfigs.isEmpty()) {
            LOGGER.info("No enabled Datadis configs found. Skipping Datadis production yearly aggregation.");
            return;
        }

        LOGGER.info("Datadis production yearly aggregation started for {} communities...", enabledConfigs.size());

        for (DatadisConfig config : enabledConfigs) {
            aggregateCommunity(config);
        }

        LOGGER.info("...finished Datadis production yearly aggregation.");
    }

    private void aggregateCommunity(DatadisConfig config) {
        try {
            LocalDate today = LocalDate.now(clock.withZone(
                    zoneResolver.resolveZoneIdForCommunity(config.getCommunityId())));
            for (int year : syncWindow.years(today)) {
                LOGGER.info("Aggregating production data for community: {}, year: {}", config.getCommunityId(), year);
                aggregationService.aggregateYearlyProductions(config.getCommunityId(), year);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to aggregate yearly production for community {}", config.getCommunityId(), e);
        }
    }
}
