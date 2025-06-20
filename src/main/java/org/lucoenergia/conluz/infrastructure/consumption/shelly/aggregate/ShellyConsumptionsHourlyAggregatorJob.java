package org.lucoenergia.conluz.infrastructure.consumption.shelly.aggregate;

import org.lucoenergia.conluz.infrastructure.shared.job.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Component
public class ShellyConsumptionsHourlyAggregatorJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShellyConsumptionsHourlyAggregatorJob.class);

    private final ShellyConsumptionsHourlyAggregatorService aggregator;

    public ShellyConsumptionsHourlyAggregatorJob(ShellyConsumptionsHourlyAggregatorService aggregator) {
        this.aggregator = aggregator;
    }

    /**
     * Aggregate instant Shelly consumptions by hour every hour.
     * The first field is for seconds. '0' here means at the beginning of the minute.
     * The second field is for the minute field. '0' here means the cron job gets executed every time the minute is '0', or, in other words, at the beginning of each hour.
     * The third field is for the hour. A '*' means "every hour".
     * The fourth field is for the day of the month. A '*' means "every day".
     * The fifth field is for the month. A '*' means "every month".
     * The sixth and final field is for the day of the week. A '*' means "every day of the week".
     */
    @Override
    @Scheduled(cron = "0 0 * * * *")
    public void run() {
        LOGGER.info("Shelly consumption hourly aggregation started...");

        // We perform the aggregation hourly between now and 5 hours before. This way, could have at least five retries
        // just in case the aggregation does not work the first time.
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);
        OffsetDateTime oneHourBefore = now.minusHours(5).truncatedTo(ChronoUnit.HOURS);

        aggregator.aggregate(oneHourBefore, now);

        LOGGER.info("...finished Shelly consumption hourly aggregation.");
    }
}
