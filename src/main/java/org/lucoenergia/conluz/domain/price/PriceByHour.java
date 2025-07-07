package org.lucoenergia.conluz.domain.price;

import java.time.OffsetDateTime;

public class PriceByHour {

    private final Double price;
    private final OffsetDateTime hour;

    public PriceByHour(Double price, OffsetDateTime hour) {
        this.price = price;
        this.hour = hour;
    }

    public Double getPrice() {
        return price;
    }

    public OffsetDateTime getHour() {
        return hour;
    }

    @Override
    public String toString() {
        return "PriceByHour{" +
                "price=" + price +
                ", hour=" + hour +
                '}';
    }
}
