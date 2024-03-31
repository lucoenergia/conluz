package org.lucoenergia.conluz.infrastructure.consumption.shelly;

import org.lucoenergia.conluz.domain.consumption.shelly.ShellyInstantConsumption;
import org.lucoenergia.conluz.infrastructure.shared.energy.EnergyMeasureConverter;
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
        if (isPowerMessage(message) && getConsumptionInKwh(message) > 0) {
            return Optional.of(new ShellyInstantConsumption.Builder()
                    .withConsumptionKWh(getConsumptionInKwh(message))
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

    private Double getConsumptionInKwh(Message<?> message) {
        return EnergyMeasureConverter.convertFromWhToKwh(Double.valueOf((String) message.getPayload()));
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
