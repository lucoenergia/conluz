package org.lucoenergia.conluz.infrastructure.consumption.shelly;

import org.influxdb.annotation.Column;
import org.influxdb.annotation.Measurement;

import java.time.Instant;

@Measurement(name = ShellyMqttPowerMessagePoint.MEASUREMENT)
public class ShellyMqttPowerMessagePoint {

    public static final String MEASUREMENT = "shelly_mqtt_power_messages";
    public static final String TOPIC = "topic";
    public static final String VALUE = "value";

    @Column(name = "time")
    private Instant time;
    @Column(name = TOPIC)
    private String topic;
    @Column(name = VALUE)
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
