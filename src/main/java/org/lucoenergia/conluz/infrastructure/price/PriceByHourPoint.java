package org.lucoenergia.conluz.infrastructure.price;

import org.influxdb.annotation.Column;
import org.influxdb.annotation.Measurement;
import org.influxdb.impl.InfluxDBResultMapper;
import org.lucoenergia.conluz.infrastructure.price.omie.OmieConfig;

import java.time.Instant;

@Measurement(name = OmieConfig.PRICES_KWH_MEASUREMENT)
public class PriceByHourPoint {

    public static final String PRICE = "price1";

    @Column(name = "time")
    private Instant time;

    @Column(name = PRICE)
    private Double price;

    /**
     * Required by {@link InfluxDBResultMapper}
     */
    public PriceByHourPoint() {
    }

    public PriceByHourPoint(Instant time, Double price) {
        this.time = time;
        this.price = price;
    }

    public Instant getTime() {
        return time;
    }

    public Double getPrice() {
        return price;
    }
}
