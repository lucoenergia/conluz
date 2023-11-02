package org.lucoenergia.conluz.domain.production;

import java.time.OffsetDateTime;

public class ProductionByTime {

    private final OffsetDateTime time;
    private final Double power;

    public ProductionByTime(OffsetDateTime time, Double power) {
        this.time = time;
        this.power = power;
    }

    public OffsetDateTime getTime() {
        return time;
    }

    public Double getPower() {
        return power;
    }
}
