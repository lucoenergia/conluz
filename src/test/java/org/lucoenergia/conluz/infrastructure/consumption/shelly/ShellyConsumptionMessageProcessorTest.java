package org.lucoenergia.conluz.infrastructure.consumption.shelly;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.consumption.shelly.ShellyInstantConsumption;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ShellyConsumptionMessageProcessorTest {

    private final ShellyConsumptionMessageScheduler scheduler = mock(ShellyConsumptionMessageScheduler.class);
    private final ShellyConsumptionMessageProcessor processor = new ShellyConsumptionMessageProcessor(scheduler);

    private final ArgumentCaptor<ShellyInstantConsumption> argumentCaptor = ArgumentCaptor.forClass(ShellyInstantConsumption.class);

    @Test
    void testOnMessage_powerMessage() {
        String topic = "shellies/123asdf654asdf8/foo/emeter/1/power";

        Message<String> message = MessageBuilder.withPayload("1.0")
                .setHeader("mqtt_receivedTopic", topic)
                .build();

        processor.onMessage(message);

        Mockito.verify(scheduler, Mockito.times(1))
                .putInQueue(argumentCaptor.capture());

        ShellyInstantConsumption capturedArgument = argumentCaptor.getValue();
        Assertions.assertNotNull(capturedArgument.getTimestamp());
        Assertions.assertEquals(0.001, capturedArgument.getConsumptionKWh());
        Assertions.assertEquals("1", capturedArgument.getChannel());
        Assertions.assertEquals("123asdf654asdf8/foo", capturedArgument.getPrefix());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "shellies/123asdf654asdf8/foo/emeter/1/total",
            "shellies/123asdf654asdf8/foo/emeter/0/voltage",
            "shellies/123asdf654asdf8/foo/emeter/1/reactive_power",
    })
    void testOnMessage_nonPowerMessage(String topic) throws InterruptedException {
        Message<String> message = MessageBuilder.withPayload("1.0")
                .setHeader("mqtt_receivedTopic", topic)
                .build();

        processor.onMessage(message);

        verifyNoInteractions(scheduler);
    }
}
