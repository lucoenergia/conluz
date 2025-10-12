package org.lucoenergia.conluz.infrastructure.production;

import java.time.Instant;

public class ProductionPoint {

    public static final String INVERTER_POWER = "inverter_power";

    private final Instant time;
    private final Double inverterPower;

    public ProductionPoint(Instant time, Double inverterPower) {
        this.time = time;
        this.inverterPower = inverterPower;
    }

    public Instant getTime() {
        return time;
    }

    public Double getInverterPower() {
        return inverterPower;
    }
}
