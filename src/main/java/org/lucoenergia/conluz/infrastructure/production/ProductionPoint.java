package org.lucoenergia.conluz.infrastructure.production;

import org.influxdb.annotation.Column;
import org.influxdb.annotation.Measurement;
import org.influxdb.impl.InfluxDBResultMapper;

import java.time.Instant;

@Measurement(name = "energy_production_huawei_hour")
public class ProductionPoint {

    @Column(name = "time")
    private Instant time;

    @Column(name = "inverter-power")
    private Double inverterPower;

    /**
     * Required by {@link InfluxDBResultMapper}
     */
    public ProductionPoint() {
    }

    public ProductionPoint(Instant time, Double inverterPower) {
        this.time = time;
        this.inverterPower = inverterPower;
    }

    public Instant getTime() {
        return time;
    }

    public Double getInverterPower() {
        return inverterPower;
    }
}
