package org.lucoenergia.conluz.infrastructure.consumption.shelly;

import java.time.Instant;

public class ShellyInstantConsumptionPoint {

    public static final String MEASUREMENT = "shelly_consumption_kw";
    public static final String PREFIX = "prefix";
    public static final String CHANNEL = "channel";
    public static final String CONSUMPTION_KW = "consumption_kw";

    private Instant time;
    private String prefix;
    private String channel;
    private Double consumptionKW;

    public Instant getTime() {
        return time;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getChannel() {
        return channel;
    }

    public Double getConsumptionKW() {
        return consumptionKW;
    }
}
