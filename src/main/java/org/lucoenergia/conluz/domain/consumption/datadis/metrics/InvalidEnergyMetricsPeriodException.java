package org.lucoenergia.conluz.domain.consumption.datadis.metrics;

/**
 * Raised when the requested energy metrics period cannot be resolved from the dates supplied.
 */
public class InvalidEnergyMetricsPeriodException extends RuntimeException {

    public enum Reason {
        /**
         * Exactly one of the two dates was supplied. The period is either fully bounded or fully
         * unbounded.
         */
        INCOMPLETE_PERIOD,
        /**
         * The start date is after the end date.
         */
        START_AFTER_END
    }

    private final Reason reason;

    public InvalidEnergyMetricsPeriodException(Reason reason) {
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
