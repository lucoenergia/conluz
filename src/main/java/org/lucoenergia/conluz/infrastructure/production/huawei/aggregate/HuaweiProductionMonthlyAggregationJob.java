package org.lucoenergia.conluz.infrastructure.production.huawei.aggregate;

import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionMonthlyAggregationService;
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

    public HuaweiProductionMonthlyAggregationJob(HuaweiProductionMonthlyAggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    @Override
    @Scheduled(cron = "0 0 2 * * ?")
    public void run() {
        LOGGER.info("Huawei production monthly aggregation started...");

        LocalDate today = LocalDate.now();
        Month month = today.getMonth();
        int year = today.getYear();

        LOGGER.info("Aggregating data for month: {}, year: {}", month, year);
        aggregationService.aggregateMonthlyProductions(month, year);

        LOGGER.info("...finished Huawei production monthly aggregation.");
    }
}
