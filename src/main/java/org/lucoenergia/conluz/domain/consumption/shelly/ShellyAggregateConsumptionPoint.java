package org.lucoenergia.conluz.domain.consumption.shelly;

import org.influxdb.annotation.Column;
import org.influxdb.annotation.Measurement;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyConfig;

import java.time.Instant;

@Measurement(name = ShellyConfig.CONSUMPTION_KW_MEASUREMENT)
public class ShellyAggregateConsumptionPoint {

    public static final String CONSUMPTION_KW = "consumption_kw";

    @Column(name = "time")
    private Instant time;
    @Column(name = CONSUMPTION_KW)
    private Double consumptionKW;

    public Instant getTime() {
        return time;
    }

    public Double getConsumptionKW() {
        return consumptionKW;
    }
}
