package org.lucoenergia.conluz.domain.consumption.shelly;

import org.influxdb.annotation.Column;
import org.influxdb.annotation.Measurement;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyConfig;

import java.time.Instant;

@Measurement(name = ShellyConfig.CONSUMPTION_KW_MEASUREMENT)
public class ShellyAggregateConsumptionPoint {

    public static final String CONSUMPTION_KWH = "consumption_kwh";

    @Column(name = "time")
    private Instant time;
    @Column(name = CONSUMPTION_KWH)
    private Double consumptionKWh;

    public Instant getTime() {
        return time;
    }

    public Double getConsumptionKWh() {
        return consumptionKWh;
    }
}
