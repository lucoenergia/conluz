package org.lucoenergia.conluz.infrastructure.production.huawei.aggregate;

import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionYearlyAggregationService;
import org.lucoenergia.conluz.domain.production.huawei.config.GetHuaweiConfigurationService;
import org.lucoenergia.conluz.domain.production.huawei.sync.HuaweiSyncWindow;
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
public class HuaweiProductionYearlyAggregationJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(HuaweiProductionYearlyAggregationJob.class);

    private final HuaweiProductionYearlyAggregationService aggregationService;
    private final Clock clock;
    private final GetHuaweiConfigurationService getHuaweiConfigurationService;
    private final HuaweiSyncWindow syncWindow;
    private final ZoneResolver zoneResolver;

    public HuaweiProductionYearlyAggregationJob(HuaweiProductionYearlyAggregationService aggregationService,
                                                Clock clock,
                                                GetHuaweiConfigurationService getHuaweiConfigurationService,
                                                HuaweiSyncWindow syncWindow,
                                                ZoneResolver zoneResolver) {
        this.aggregationService = aggregationService;
        this.clock = clock;
        this.getHuaweiConfigurationService = getHuaweiConfigurationService;
        this.syncWindow = syncWindow;
        this.zoneResolver = zoneResolver;
    }

    /**
     * Aggregates monthly production into yearly totals at 3:00 AM every day.
     * For the plant of every enabled Huawei configuration, this job re-aggregates every year that
     * the months of the monthly aggregation overlap ({@link HuaweiSyncWindow#years(LocalDate)}), not
     * only the current one: early in January the monthly aggregation rewrites December, so the
     * previous year's total would otherwise stay frozen without its last day.
     *
     * The current date is resolved in the plant's zone, so the window follows the plant's calendar
     * rather than the JVM default zone. It reads the monthly measurement, so it runs after the
     * monthly aggregation (2:00 AM) has rewritten it.
     */
    @Override
    @Scheduled(cron = "0 0 3 * * ?")
    public void run() {
        List<HuaweiConfig> enabledConfigs = getHuaweiConfigurationService.findAllEnabled();
        if (enabledConfigs.isEmpty()) {
            LOGGER.info("No enabled Huawei configs found. Skipping Huawei production yearly aggregation.");
            return;
        }

        LOGGER.info("Huawei production yearly aggregation started for {} configs...", enabledConfigs.size());

        for (HuaweiConfig config : enabledConfigs) {
            aggregatePlant(config);
        }

        LOGGER.info("...finished Huawei production yearly aggregation.");
    }

    private void aggregatePlant(HuaweiConfig config) {
        if (config.getPlantId() == null) {
            LOGGER.warn("Huawei config {} has no plant assigned, skipping.", config.getId());
            return;
        }
        try {
            LocalDate today = LocalDate.now(clock.withZone(zoneResolver.resolveZoneId(config.getPlantId())));
            for (int year : syncWindow.years(today)) {
                LOGGER.info("Aggregating data for plant: {}, year: {}", config.getPlantId(), year);
                aggregationService.aggregatePlantYearlyProduction(config.getPlantId(), year);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to aggregate yearly production for plant {}", config.getPlantId(), e);
        }
    }
}
