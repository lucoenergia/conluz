package org.lucoenergia.conluz.domain.production.plant.surplusclaim;

import java.time.YearMonth;

/**
 * A surplus claim was requested for a month that has not ended yet: only closed months can be claimed.
 */
public class SurplusClaimMonthNotClosedException extends RuntimeException {

    private final YearMonth month;
    private final YearMonth currentMonth;

    public SurplusClaimMonthNotClosedException(YearMonth month, YearMonth currentMonth) {
        this.month = month;
        this.currentMonth = currentMonth;
    }

    public YearMonth getMonth() {
        return month;
    }

    /**
     * The month in progress in the zone of the plant's community, the first month that cannot be claimed.
     */
    public YearMonth getCurrentMonth() {
        return currentMonth;
    }
}
