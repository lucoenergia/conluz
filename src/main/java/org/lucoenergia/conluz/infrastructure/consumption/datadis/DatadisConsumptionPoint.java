package org.lucoenergia.conluz.infrastructure.consumption.datadis;

import org.influxdb.annotation.Column;
import org.influxdb.annotation.Measurement;
import org.lucoenergia.conluz.infrastructure.shared.datadis.DatadisConfig;

import java.time.Instant;

@Measurement(name = DatadisConfig.CONSUMPTION_KWH_MEASUREMENT)
public class DatadisConsumptionPoint {

    @Column(name = "time")
    private Instant time;
    @Column(name = "cups", tag = true)
    private String cups;
    @Column(name = "consumption_kwh")
    private Double consumptionKWh;
    @Column(name = "obtain_method")
    private String obtainMethod;
    @Column(name = "surplus_energy_kwh")
    private Double surplusEnergyKWh;

    public Instant getTime() {
        return time;
    }

    public String getCups() {
        return cups;
    }

    public Double getConsumptionKWh() {
        return consumptionKWh;
    }

    public String getObtainMethod() {
        return obtainMethod;
    }

    public Double getSurplusEnergyKWh() {
        return surplusEnergyKWh;
    }
}
