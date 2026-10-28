package org.lucoenergia.conluz.domain.price.sync;

import java.time.LocalDate;

/**
 * The rules a range of market days requested for a price synchronisation must satisfy (PRC-005).
 */
public final class PriceSyncPeriodValidator {

    /**
     * The longest range that may be synchronised, in days. Each day is one call to OMIE with a 30-second timeout.
     */
    public static final int MAX_DAYS = 31;

    private PriceSyncPeriodValidator() {
    }

    /**
     * The range {@code [startDate, endDate)} is half-open, so the start must be strictly before the end; it must be
     * at most {@link #MAX_DAYS} days long; and it must extend no further than tomorrow, the last day OMIE publishes,
     * so the end must be no later than the day after tomorrow. A range beyond tomorrow is rejected, never clipped.
     *
     * @param today today in Europe/Madrid
     * @throws InvalidPriceSyncPeriodException when the first of those rules, in that order, is broken
     */
    public static void validate(LocalDate startDate, LocalDate endDate, LocalDate today) {
        if (!startDate.isBefore(endDate)) {
            throw new InvalidPriceSyncPeriodException(InvalidPriceSyncPeriodException.Reason.START_AFTER_END);
        }
        if (endDate.isAfter(startDate.plusDays(MAX_DAYS))) {
            throw new InvalidPriceSyncPeriodException(InvalidPriceSyncPeriodException.Reason.TOO_LONG);
        }
        if (endDate.isAfter(today.plusDays(2))) {
            throw new InvalidPriceSyncPeriodException(InvalidPriceSyncPeriodException.Reason.END_BEYOND_TOMORROW);
        }
    }
}
