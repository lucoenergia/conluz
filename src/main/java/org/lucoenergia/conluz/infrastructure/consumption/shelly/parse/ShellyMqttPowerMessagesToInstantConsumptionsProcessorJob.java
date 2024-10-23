package org.lucoenergia.conluz.infrastructure.consumption.shelly.parse;

import org.lucoenergia.conluz.infrastructure.shared.job.Job;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class ShellyMqttPowerMessagesToInstantConsumptionsProcessorJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShellyMqttPowerMessagesToInstantConsumptionsProcessorJob.class);

    private final ShellyMqttPowerMessagesToInstantConsumptionsProcessor processor;
    private final TimeConfiguration timeConfiguration;

    public ShellyMqttPowerMessagesToInstantConsumptionsProcessorJob(ShellyMqttPowerMessagesToInstantConsumptionsProcessor processor,
                                                                    TimeConfiguration timeConfiguration) {
        this.processor = processor;
        this.timeConfiguration = timeConfiguration;
    }

    /**
     * Aggregate instant Shelly consumptions by hour every hour.
     * The first field is for seconds. '*\/30' here means that runs every 10 seconds.
     * The second field is for the minute field. A '*' means "every minute".
     * The third field is for the hour. A '*' means "every hour".
     * The fourth field is for the day of the month. A '*' means "every day".
     * The fifth field is for the month. A '*' means "every month".
     * The sixth and final field is for the day of the week. A '*' means "every day of the week".
     */
    @Override
    @Scheduled(cron = "*/30 * * * * *")
    public void run() {
        LOGGER.info("Shelly MQTT power message to instant consumption processor started...");

        // We perform the process every 30 seconds between now and 5 minutes before. This way, could have at least 10 retries
        // just in case the process does not work the first time.
        OffsetDateTime now = timeConfiguration.now();
        OffsetDateTime periodBefore = now.minusMinutes(5);

        processor.process(periodBefore, now);

        LOGGER.info("...finished Shelly MQTT power message to instant consumption processor.");
    }
}
