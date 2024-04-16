package org.lucoenergia.conluz.domain.consumption.shelly;

import org.influxdb.annotation.Column;
import org.influxdb.annotation.Measurement;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyInstantConsumptionPoint;

import java.time.Instant;

@Measurement(name = ShellyInstantConsumptionPoint.MEASUREMENT)
public class ShellyAggregateConsumptionPoint {

    @Column(name = "time")
    private Instant time;
    @Column(name = ShellyInstantConsumptionPoint.CONSUMPTION_KW)
    private Double consumptionKW;

    public Instant getTime() {
        return time;
    }

    public Double getConsumptionKW() {
        return consumptionKW;
    }
}
