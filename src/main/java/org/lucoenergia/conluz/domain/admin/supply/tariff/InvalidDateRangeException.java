package org.lucoenergia.conluz.domain.admin.supply.tariff;

/**
 * Raised when a {@link DateRange} cannot be built from the bounds supplied.
 *
 * <p>This signals a programming error in the code constructing the range, not bad client
 * input: no request payload reaches {@link DateRange} without a service first translating
 * it. It is therefore deliberately left unmapped to any HTTP status.
 */
public class InvalidDateRangeException extends RuntimeException {

    public enum Reason {
        /**
         * The start date was null.
         */
        NULL_START,
        /**
         * The end date was null.
         */
        NULL_END,
        /**
         * The start date is after the end date.
         */
        START_AFTER_END,
        /**
         * Start and end are the same date. Under the half-open {@code [start, end)}
         * semantics that is a range covering no day at all.
         */
        EMPTY_RANGE
    }

    private final Reason reason;

    public InvalidDateRangeException(Reason reason) {
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
