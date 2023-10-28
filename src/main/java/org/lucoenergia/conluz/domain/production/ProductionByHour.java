package org.lucoenergia.conluz.domain.production;

import java.time.OffsetDateTime;

public class ProductionByHour {

    private final OffsetDateTime hour;
    private final Double power;

    public ProductionByHour(OffsetDateTime hour, Double power) {
        this.hour = hour;
        this.power = power;
    }

    public OffsetDateTime getHour() {
        return hour;
    }

    public Double getPower() {
        return power;
    }
}
