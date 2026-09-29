package org.lucoenergia.conluz.domain.consumption;

/**
 * A period of energy metrics the server resolves by itself, instead of the caller bounding it with
 * explicit dates.
 */
public enum EnergyMetricsReferencePeriod {

    /**
     * The most recent complete calendar month with published assigned production, as resolved by
     * {@link ReferenceMonthResolver}.
     */
    LATEST_PUBLISHED_MONTH
}
