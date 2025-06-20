package org.lucoenergia.conluz.infrastructure.consumption.shelly.parse;

import org.lucoenergia.conluz.infrastructure.shared.job.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Component
public class ShellyMqttPowerMessagesToInstantConsumptionsProcessorJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShellyMqttPowerMessagesToInstantConsumptionsProcessorJob.class);

    private final ShellyMqttPowerMessagesToInstantConsumptionsProcessor processor;

    public ShellyMqttPowerMessagesToInstantConsumptionsProcessorJob(ShellyMqttPowerMessagesToInstantConsumptionsProcessor processor) {
        this.processor = processor;
    }

    /**
     * Scheduled job that processes Shelly MQTT power messages and converts them into instant consumption data.
     *
     * This method is executed every 30 seconds as specified by the cron expression. It calculates
     * a time range of the last 5 minutes and delegates the processing of messages within this
     * range to the associated processor.
     *
     * The processing workflow involves fetching power messages from the data repository within the
     * specified time range and persisting the converted instant consumption data.
     *
     * Behavior:
     * - Logs the start and end of the processing task for debugging purposes.
     * - Retrieves messages for the time range using the provided processor.
     * - Executes retry logic indirectly through the frequent job executions, ensuring multiple
     *   opportunities to process missed messages.
     */
    @Override
    @Scheduled(cron = "*/30 * * * * *")
    public void run() {
        LOGGER.debug("Shelly MQTT power message to instant consumption processor started...");

        // We perform the process every 30 seconds between now and 5 minutes before. This way, could have at least 10 retries
        // just in case the process does not work the first time.
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES);
        OffsetDateTime periodBefore = now.minusMinutes(5);

        processor.process(periodBefore, now);

        LOGGER.debug("...finished Shelly MQTT power message to instant consumption processor.");
    }
}
