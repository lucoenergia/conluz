package org.lucoenergia.conluz.infrastructure.production;

import org.influxdb.annotation.Column;
import org.influxdb.annotation.Measurement;
import org.influxdb.impl.InfluxDBResultMapper;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;

import java.time.Instant;

@Measurement(name = HuaweiConfig.HUAWEI_YEARLY_PRODUCTION_MEASUREMENT)
public class HuaweiHourlyProductionYearlyPoint {

    @Column(name = "time")
    private Instant time;

    @Column(name = "station_code", tag = true)
    private String stationCode;

    @Column(name = "inverter_power")
    private Double inverterPower;

    @Column(name = "ongrid_power")
    private Double ongridPower;

    @Column(name = "power_profit")
    private Double powerProfit;

    @Column(name = "theory_power")
    private Double theoryPower;

    @Column(name = "radiation_intensity")
    private Double radiationIntensity;

    /**
     * Required by {@link InfluxDBResultMapper}
     */
    public HuaweiHourlyProductionYearlyPoint() {
    }

    public Instant getTime() {
        return time;
    }

    public String getStationCode() {
        return stationCode;
    }

    public Double getInverterPower() {
        return inverterPower;
    }

    public Double getOngridPower() {
        return ongridPower;
    }

    public Double getPowerProfit() {
        return powerProfit;
    }

    public Double getTheoryPower() {
        return theoryPower;
    }

    public Double getRadiationIntensity() {
        return radiationIntensity;
    }
}
