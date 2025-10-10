package org.lucoenergia.conluz.infrastructure.consumption.shelly;

import java.time.Instant;

public class ShellyMqttPowerMessagePoint {

    public static final String MEASUREMENT = "shelly_mqtt_power_messages";
    public static final String TOPIC = "topic";
    public static final String VALUE = "value";

    private Instant time;
    private String topic;
    private Double value;

    public Instant getTime() {
        return time;
    }

    public String getTopic() {
        return topic;
    }

    public Double getValue() {
        return value;
    }
}
