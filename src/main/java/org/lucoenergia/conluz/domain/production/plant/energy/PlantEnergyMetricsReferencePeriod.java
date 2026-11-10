package org.lucoenergia.conluz.domain.production.plant.energy;

/**
 * A period of a plant's energy metrics the server resolves by itself, instead of the caller bounding
 * it with explicit dates.
 */
public enum PlantEnergyMetricsReferencePeriod {

    /**
     * The latest local day before the current one, within the 24 months before it, whose stored
     * production records cover all its hours.
     */
    LATEST_PRODUCTION_DAY
}
