package org.lucoenergia.conluz.domain.admin.supply.tariff;

/**
 * Raised when a {@link TaxRatePeriods} cannot be built from the periods and the default rate supplied.
 *
 * <p>The periods come from configuration, so this signals a deployment that must not start, not bad
 * client input: it is deliberately left unmapped to any HTTP status. It carries the index of the
 * offending period, so the code reading the configuration can name the property to fix.
 */
public class InvalidTaxRatePeriodsException extends RuntimeException {

    public enum Reason {
        /**
         * A period has no start date.
         */
        FROM_MISSING,
        /**
         * A period has no rate.
         */
        RATE_MISSING,
        /**
         * A period ends on or before the date it starts, so it covers no day at all.
         */
        PERIOD_EMPTY,
        /**
         * A period starts before its predecessor starts.
         */
        UNORDERED,
        /**
         * A period starts before its predecessor ends, so two rates would apply to the same day.
         */
        OVERLAP,
        /**
         * A period starts after its predecessor ends, leaving days in between with no configured rate.
         */
        GAP,
        /**
         * A period with no end is followed by another one.
         */
        OPEN_ENDED_NOT_LAST,
        /**
         * A period's rate is negative, or 1 or above.
         */
        RATE_OUT_OF_RANGE,
        /**
         * The default rate is absent.
         */
        DEFAULT_RATE_MISSING,
        /**
         * The default rate is negative, or 1 or above.
         */
        DEFAULT_RATE_OUT_OF_RANGE
    }

    private final Reason reason;
    private final Integer periodIndex;

    public InvalidTaxRatePeriodsException(Reason reason, Integer periodIndex) {
        super("Invalid tax rate periods: " + reason + (periodIndex == null ? "" : " at period " + periodIndex));
        this.reason = reason;
        this.periodIndex = periodIndex;
    }

    public Reason getReason() {
        return reason;
    }

    /**
     * The index of the period the violation was detected at, or null when it concerns the default rate.
     */
    public Integer getPeriodIndex() {
        return periodIndex;
    }
}
