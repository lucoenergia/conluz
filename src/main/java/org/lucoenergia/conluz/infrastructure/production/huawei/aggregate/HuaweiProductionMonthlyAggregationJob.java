package org.lucoenergia.conluz.infrastructure.production.huawei.aggregate;

import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionMonthlyAggregationService;
import org.lucoenergia.conluz.domain.production.huawei.config.GetHuaweiConfigurationService;
import org.lucoenergia.conluz.infrastructure.shared.job.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Month;

@Component
public class HuaweiProductionMonthlyAggregationJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(HuaweiProductionMonthlyAggregationJob.class);

    private final HuaweiProductionMonthlyAggregationService aggregationService;
    private final GetHuaweiConfigurationService getHuaweiConfigurationService;

    public HuaweiProductionMonthlyAggregationJob(HuaweiProductionMonthlyAggregationService aggregationService,
            GetHuaweiConfigurationService getHuaweiConfigurationService) {
        this.aggregationService = aggregationService;
        this.getHuaweiConfigurationService = getHuaweiConfigurationService;
    }

    /**
     * Aggregate hourly production data into monthly totals at 2:00 AM every day.
     * This job aggregates data for the current month and year for all plants.
     *
     * Cron expression breakdown:
     * 0 seconds (at the start of the minute)
     * 0 minutes (at the start of the hour)
     * 2 (at 2 AM)
     * * (every day)
     * * (every month)
     * ? (any day of the week)
     *
     * Rationale:
     * - Runs at 2 AM to run after the previous-days Huawei hourly sync (1 AM)
     *   and before the Datadis aggregation jobs (5 AM, 6 AM).
     * - Runs every day to keep aggregated monthly production up to date.
     */
    @Override
    @Scheduled(cron = "0 0 2 * * ?")
    public void run() {
        if (getHuaweiConfigurationService.isDisabled()) {
            LOGGER.debug("Huawei configuration is disabled. Skipping monthly production aggregation.");
            return;
        }
        LOGGER.info("Huawei production monthly aggregation started...");

        LocalDate today = LocalDate.now();
        Month month = today.getMonth();
        int year = today.getYear();

        LOGGER.info("Aggregating data for month: {}, year: {}", month, year);
        aggregationService.aggregateMonthlyProductions(month, year);

        LOGGER.info("...finished Huawei production monthly aggregation.");
    }
}
