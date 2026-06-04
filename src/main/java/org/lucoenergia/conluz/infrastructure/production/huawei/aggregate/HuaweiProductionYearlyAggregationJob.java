package org.lucoenergia.conluz.infrastructure.production.huawei.aggregate;

import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionYearlyAggregationService;
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

    public HuaweiProductionYearlyAggregationJob(HuaweiProductionYearlyAggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    @Override
    @Scheduled(cron = "0 0 3 * * ?")
    public void run() {
        LOGGER.info("Huawei production yearly aggregation started...");

        LocalDate today = LocalDate.now();
        int year = today.getYear();

        LOGGER.info("Aggregating data for year: {}", year);
        aggregationService.aggregateYearlyProductions(year);

        LOGGER.info("...finished Huawei production yearly aggregation.");
    }
}
