package org.lucoenergia.conluz.domain.price.sync;

/**
 * Raised when the range of market days requested for a price synchronisation is not one that may be synchronised.
 */
public class InvalidPriceSyncPeriodException extends RuntimeException {

    public enum Reason {
        /**
         * The start date is not strictly before the end date: after it, or equal to it, which makes the half-open
         * range empty.
         */
        START_AFTER_END,
        /**
         * The range is longer than {@link PriceSyncPeriodValidator#MAX_DAYS} days.
         */
        TOO_LONG,
        /**
         * The range extends beyond tomorrow in Europe/Madrid: the end date is after the day after tomorrow.
         */
        END_BEYOND_TOMORROW
    }

    private final Reason reason;

    public InvalidPriceSyncPeriodException(Reason reason) {
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
