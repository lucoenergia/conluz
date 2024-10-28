package org.lucoenergia.conluz.infrastructure.production;

import org.influxdb.annotation.Column;
import org.influxdb.annotation.Measurement;
import org.influxdb.impl.InfluxDBResultMapper;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;

import java.time.Instant;

@Measurement(name = HuaweiConfig.HUAWEI_HOURLY_PRODUCTION_MEASUREMENT)
public class ProductionPoint {

    public static final String INVERTER_POWER = "inverter_power";

    @Column(name = "time")
    private Instant time;

    @Column(name = INVERTER_POWER)
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
