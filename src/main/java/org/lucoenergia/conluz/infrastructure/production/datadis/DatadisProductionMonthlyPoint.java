package org.lucoenergia.conluz.infrastructure.production.datadis;

import org.influxdb.annotation.Column;
import org.influxdb.annotation.Measurement;

import java.time.Instant;

@Measurement(name = DatadisProductionMeasurements.PRODUCTION_KWH_MONTH_MEASUREMENT)
public class DatadisProductionMonthlyPoint {

    @Column(name = "time")
    private Instant time;
    @Column(name = "cups", tag = true)
    private String cups;
    @Column(name = "production_kwh")
    private Double productionKWh;
    @Column(name = "obtain_method")
    private String obtainMethod;

    public Instant getTime() {
        return time;
    }

    public String getCups() {
        return cups;
    }

    public Double getProductionKWh() {
        return productionKWh;
    }

    public String getObtainMethod() {
        return obtainMethod;
    }
}
