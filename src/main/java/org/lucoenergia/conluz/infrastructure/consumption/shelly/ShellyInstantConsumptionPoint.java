package org.lucoenergia.conluz.infrastructure.consumption.shelly;

import org.influxdb.annotation.Column;
import org.influxdb.annotation.Measurement;

import java.time.Instant;

@Measurement(name = ShellyConfig.CONSUMPTION_KW_MEASUREMENT)
public class ShellyInstantConsumptionPoint {

    public static final String PREFIX = "prefix";
    public static final String CHANNEL = "channel";
    public static final String CONSUMPTION_KW = "consumption_kw";

    @Column(name = "time")
    private Instant time;
    @Column(name = PREFIX, tag = true)
    private String prefix;
    @Column(name = CHANNEL, tag = true)
    private String channel;
    @Column(name = CONSUMPTION_KW)
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
