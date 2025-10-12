package org.lucoenergia.conluz.infrastructure.consumption.shelly;

import java.time.Instant;

public class ShellyConsumptionPoint {

    public static final String MEASUREMENT = "shelly_consumption_kwh";
    public static final String PREFIX = "prefix";
    public static final String CONSUMPTION_KWH = "consumption_kwh";

    private Instant time;
    private String prefix;
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
