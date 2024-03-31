package org.lucoenergia.conluz.infrastructure.shared.energy;

public class EnergyMeasureConverter {

    private EnergyMeasureConverter() {
    }

    public static Double convertFromWhToKwh(Double energyInWh) {
        return energyInWh / 1000;
    }
}
