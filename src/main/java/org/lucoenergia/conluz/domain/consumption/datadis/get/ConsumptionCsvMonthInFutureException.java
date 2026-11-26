package org.lucoenergia.conluz.domain.consumption.datadis.get;

import java.time.YearMonth;

/**
 * A Datadis consumption CSV was requested for a month after the current one: no record can exist for it yet.
 */
public class ConsumptionCsvMonthInFutureException extends RuntimeException {

    private final YearMonth month;
    private final YearMonth currentMonth;

    public ConsumptionCsvMonthInFutureException(YearMonth month, YearMonth currentMonth) {
        this.month = month;
        this.currentMonth = currentMonth;
    }

    public YearMonth getMonth() {
        return month;
    }

    /**
     * The month in progress in the application's time zone, the last month that can be requested.
     */
    public YearMonth getCurrentMonth() {
        return currentMonth;
    }
}
