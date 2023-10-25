package org.lucoenergia.conluz.price;

import java.time.OffsetDateTime;

public class PriceByHour {

    private Double price;
    private OffsetDateTime time;

    public PriceByHour(Double price, OffsetDateTime time) {
        this.price = price;
        this.time = time;
    }

    public Double getPrice() {
        return price;
    }

    public OffsetDateTime getTime() {
        return time;
    }
}
