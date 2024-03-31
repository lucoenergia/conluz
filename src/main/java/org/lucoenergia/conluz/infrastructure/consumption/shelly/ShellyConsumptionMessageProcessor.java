package org.lucoenergia.conluz.infrastructure.consumption.shelly;

import org.lucoenergia.conluz.domain.consumption.shelly.ShellyInstantConsumption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
public class ShellyConsumptionMessageProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShellyConsumptionMessageProcessor.class);

    private static final String HEADER_RECEIVED_TOPIC = "mqtt_receivedTopic";

    private final ShellyConsumptionMessageScheduler shellyConsumptionMessageScheduler;

    public ShellyConsumptionMessageProcessor(ShellyConsumptionMessageScheduler shellyConsumptionMessageScheduler) {
        this.shellyConsumptionMessageScheduler = shellyConsumptionMessageScheduler;
    }

    public void onMessage(Message<?> message) {
        Optional<ShellyInstantConsumption> consumptionMessage;
        try {
            consumptionMessage = processMessage(message);
        } catch (Exception e) {
            LOGGER.error("Unable to process message {}. Reason: {}", message, e.getMessage());
            return;
        }
        consumptionMessage.ifPresent(shellyConsumptionMessageScheduler::putInQueue);
    }

    private Optional<ShellyInstantConsumption> processMessage(Message<?> message) {
        if (isPowerMessage(message) && getConsumptionInKw(message) > 0) {
            return Optional.of(new ShellyInstantConsumption.Builder()
                    .withConsumptionKW(getConsumptionInKw(message))
                    .withTimestamp(getTimestamp(message))
                    .withPrefix(getPrefix(message))
                    .withChannel(getChannel(message))
                    .build());
        }
        return Optional.empty();
    }

    private boolean isPowerMessage(Message<?> message) {
        String topic = (String) message.getHeaders().get(HEADER_RECEIVED_TOPIC);
        return topic.endsWith("/power");
    }

    private Double getConsumptionInKw(Message<?> message) {
        return convertFromWToKW(Double.valueOf((String) message.getPayload()));
    }

    public static Double convertFromWToKW(Double energyInW) {
        return energyInW / 1000;
    }

    private Instant getTimestamp(Message<?> message) {
        return Instant.ofEpochMilli(message.getHeaders().getTimestamp());
    }

    private String getPrefix(Message<?> message) {
        String topic = (String) message.getHeaders().get(HEADER_RECEIVED_TOPIC);
        String[] slices = topic.split("/");
        return slices[1] + "/" + slices[2];
    }

    private String getChannel(Message<?> message) {
        String topic = (String) message.getHeaders().get(HEADER_RECEIVED_TOPIC);
        String[] slices = topic.split("/");
        return slices[4];
    }
}
