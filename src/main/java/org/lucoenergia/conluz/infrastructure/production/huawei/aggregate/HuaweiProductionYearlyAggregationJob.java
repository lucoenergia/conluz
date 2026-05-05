package org.lucoenergia.conluz.infrastructure.production.huawei.aggregate;

import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionYearlyAggregationService;
import org.lucoenergia.conluz.domain.production.huawei.config.GetHuaweiConfigurationService;
import org.lucoenergia.conluz.infrastructure.shared.job.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class HuaweiProductionYearlyAggregationJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(HuaweiProductionYearlyAggregationJob.class);

    private final HuaweiProductionYearlyAggregationService aggregationService;
    private final GetHuaweiConfigurationService getHuaweiConfigurationService;

    public HuaweiProductionYearlyAggregationJob(HuaweiProductionYearlyAggregationService aggregationService,
            GetHuaweiConfigurationService getHuaweiConfigurationService) {
        this.aggregationService = aggregationService;
        this.getHuaweiConfigurationService = getHuaweiConfigurationService;
    }

    /**
     * Aggregate monthly production data into yearly totals at 3:00 AM every day.
     * This job aggregates data for the current year for all plants.
     *
     * Cron expression breakdown:
     * 0 seconds (at the start of the minute)
     * 0 minutes (at the start of the hour)
     * 3 (at 3 AM)
     * * (every day)
     * * (every month)
     * ? (any day of the week)
     *
     * Rationale:
     * - Runs at 3 AM after the monthly aggregation job (2 AM) to ensure monthly data is ready.
     * - Runs every day to keep aggregated yearly production up to date.
     */
    @Override
    @Scheduled(cron = "0 0 3 * * ?")
    public void run() {
        if (getHuaweiConfigurationService.isDisabled()) {
            LOGGER.debug("Huawei configuration is disabled. Skipping yearly production aggregation.");
            return;
        }
        LOGGER.info("Huawei production yearly aggregation started...");

        LocalDate today = LocalDate.now();
        int year = today.getYear();

        LOGGER.info("Aggregating data for year: {}", year);
        aggregationService.aggregateYearlyProductions(year);

        LOGGER.info("...finished Huawei production yearly aggregation.");
    }
}
