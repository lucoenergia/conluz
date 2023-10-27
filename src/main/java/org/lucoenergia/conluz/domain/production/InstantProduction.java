package org.lucoenergia.conluz.domain.production;

public class InstantProduction {

    private final Double power;

    public InstantProduction(Double power) {
        this.power = power;
    }

    public Double getPower() {
        return power;
    }
}
