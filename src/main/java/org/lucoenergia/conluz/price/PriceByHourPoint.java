package org.lucoenergia.conluz.price;

import org.influxdb.annotation.Column;
import org.influxdb.annotation.Measurement;

import java.time.Instant;

@Measurement(name = "omie-daily-prices")
public class PriceByHourPoint {

    @Column(name = "time")
    private Instant time;

    @Column(name = "price1")
    private Double price1;

    @Column(name = "price2")
    private Double price2;

    public PriceByHourPoint() {
    }

    public PriceByHourPoint(Instant time, Double price1, Double price2) {
        this.time = time;
        this.price1 = price1;
        this.price2 = price2;
    }

    public Instant getTime() {
        return time;
    }

    public Double getPrice1() {
        return price1;
    }

    public Double getPrice2() {
        return price2;
    }
}
