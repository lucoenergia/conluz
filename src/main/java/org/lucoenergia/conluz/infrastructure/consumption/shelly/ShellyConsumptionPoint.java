package org.lucoenergia.conluz.infrastructure.consumption.shelly;

import org.influxdb.annotation.Column;
import org.influxdb.annotation.Measurement;

import java.time.Instant;

@Measurement(name = ShellyConsumptionPoint.MEASUREMENT)
public class ShellyConsumptionPoint {

    public static final String MEASUREMENT = "shelly_consumption_kwh";
    public static final String PREFIX = "prefix";
    public static final String CONSUMPTION_KWH = "consumption_kwh";

    @Column(name = "time")
    private Instant time;
    @Column(name = PREFIX, tag = true)
    private String prefix;
    @Column(name = CONSUMPTION_KWH)
    private Double consumptionKWh;

    public Instant getTime() {
        return time;
    }

    public String getPrefix() {
        return prefix;
    }

    public Double getConsumptionKWh() {
        return consumptionKWh;
    }
}
