package org.lucoenergia.conluz.infrastructure.price;


import java.time.Instant;

public class PriceByHourPoint {

    public static final String PRICE = "price1";

    private final Instant time;
    private final Double price;

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
