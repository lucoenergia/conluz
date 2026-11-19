package org.lucoenergia.conluz.domain.production.plant.energy;

/**
 * Where a plant supply's energy over one local day is read from.
 */
public enum PlantEnergySource {

    /**
     * The supply's {@code production_kwh} records: every day from its first one on.
     */
    PRODUCTION,

    /**
     * The supply's {@code surplus_energy_kwh} records in the consumption measurement, where its production was
     * stored before it was stored apart: every day before its first production record.
     */
    LEGACY_SURPLUS
}
