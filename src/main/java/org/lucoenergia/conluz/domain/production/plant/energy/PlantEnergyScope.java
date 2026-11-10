package org.lucoenergia.conluz.domain.production.plant.energy;

import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;

import java.time.ZoneId;
import java.util.Optional;

/**
 * What the energy figures of a plant are computed over: the code (CUPS) of the plant's backing supply,
 * the time zone of the plant's community, and the period, or no period when none could be resolved.
 *
 * <p>The supply code is what the plant's records are stored under. It never leaves the domain: no
 * response carries it.
 */
public class PlantEnergyScope {

    private final String supplyCode;
    private final ZoneId zone;
    private final EnergyMetricsPeriod period;

    public PlantEnergyScope(String supplyCode, ZoneId zone, EnergyMetricsPeriod period) {
        this.supplyCode = supplyCode;
        this.zone = zone;
        this.period = period;
    }

    /**
     * The code (CUPS) of the plant's backing supply.
     */
    public String getSupplyCode() {
        return supplyCode;
    }

    /**
     * The time zone of the plant's community, the one the period and its buckets are read in.
     */
    public ZoneId getZone() {
        return zone;
    }

    /**
     * The period the figures are computed over, or empty when none could be resolved.
     */
    public Optional<EnergyMetricsPeriod> getPeriod() {
        return Optional.ofNullable(period);
    }
}
