package org.lucoenergia.conluz.domain.production.plant.surplusclaim;

/**
 * Whether a day of a surplus claim is built from a record of every supply in scope for every hour.
 */
public enum SurplusClaimDayState {
    /**
     * Every supply in scope stored a surplus record for every hour of the day.
     */
    REAL,
    /**
     * Some supply in scope misses a surplus record for some hour of the day, or no supply is in scope.
     */
    ESTIMATED
}
