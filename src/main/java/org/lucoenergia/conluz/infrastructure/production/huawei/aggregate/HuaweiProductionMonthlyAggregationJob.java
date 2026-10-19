package org.lucoenergia.conluz.infrastructure.production.huawei.aggregate;

import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionMonthlyAggregationService;
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
import java.time.YearMonth;
import java.util.List;

@Component
public class HuaweiProductionMonthlyAggregationJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(HuaweiProductionMonthlyAggregationJob.class);

    private final HuaweiProductionMonthlyAggregationService aggregationService;
    private final Clock clock;
    private final GetHuaweiConfigurationService getHuaweiConfigurationService;
    private final HuaweiSyncWindow syncWindow;
    private final ZoneResolver zoneResolver;

    public HuaweiProductionMonthlyAggregationJob(HuaweiProductionMonthlyAggregationService aggregationService,
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
     * Aggregates hourly production into monthly totals at 2:00 AM every day.
     * For the plant of every enabled Huawei configuration, this job re-aggregates every month that
     * overlaps the window of the previous-days sync, up to and including today
     * ({@link HuaweiSyncWindow#months(LocalDate)}), not only the current one: a month's last run
     * happens at 2:00 AM on its last day, before that day is synced, so a closed month's total would
     * otherwise miss its last day for good.
     *
     * The current date is resolved in the plant's zone, so the window follows the plant's calendar
     * rather than the JVM default zone.
     */
    @Override
    @Scheduled(cron = "0 0 2 * * ?")
    public void run() {
        List<HuaweiConfig> enabledConfigs = getHuaweiConfigurationService.findAllEnabled();
        if (enabledConfigs.isEmpty()) {
            LOGGER.info("No enabled Huawei configs found. Skipping Huawei production monthly aggregation.");
            return;
        }

        LOGGER.info("Huawei production monthly aggregation started for {} configs...", enabledConfigs.size());

        for (HuaweiConfig config : enabledConfigs) {
            aggregatePlant(config);
        }

        LOGGER.info("...finished Huawei production monthly aggregation.");
    }

    private void aggregatePlant(HuaweiConfig config) {
        if (config.getPlantId() == null) {
            LOGGER.warn("Huawei config {} has no plant assigned, skipping.", config.getId());
            return;
        }
        try {
            LocalDate today = LocalDate.now(clock.withZone(zoneResolver.resolveZoneId(config.getPlantId())));
            for (YearMonth month : syncWindow.months(today)) {
                LOGGER.info("Aggregating data for plant: {}, month: {}", config.getPlantId(), month);
                aggregationService.aggregatePlantMonthlyProduction(config.getPlantId(), month.getMonth(),
                        month.getYear());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to aggregate monthly production for plant {}", config.getPlantId(), e);
        }
    }
}
