package org.lucoenergia.conluz.domain.consumption;

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
         * The start date is not strictly before the end date: after it, or equal to it, which
         * makes the half-open period empty.
         */
        START_AFTER_END,
        /**
         * The start date is at or after the start of the current hour: the period has not begun.
         */
        START_IN_FUTURE,
        /**
         * The end date is later than the start date plus the longest period that may be
         * requested.
         */
        TOO_LONG,
        /**
         * A reference period was requested together with explicit dates. The period is either
         * resolved by the server or supplied by the caller, never both.
         */
        CONFLICTING_PERIOD
    }

    private final Reason reason;

    public InvalidEnergyMetricsPeriodException(Reason reason) {
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
