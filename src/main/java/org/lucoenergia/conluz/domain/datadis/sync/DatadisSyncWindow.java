package org.lucoenergia.conluz.domain.datadis.sync;

import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

/**
 * How far back the daily Datadis sync re-reads hourly records, so that revisions Datadis publishes
 * after a month has closed reach the stored hourly series.
 *
 * <p>The single definition of that window: anything that has to cover the same period as the sync
 * derives it from here rather than restating it.
 *
 * @param lookback how far back from today the window reaches; the window then starts on the first
 *                 day of the month that point falls in
 */
public record DatadisSyncWindow(Period lookback) {

    public static final DatadisSyncWindow DEFAULT = new DatadisSyncWindow(Period.ofYears(1));

    public DatadisSyncWindow {
        Objects.requireNonNull(lookback, "lookback");
        if (lookback.isNegative()) {
            throw new IllegalArgumentException("The sync window lookback cannot be negative: " + lookback);
        }
    }

    /**
     * The first day the sync re-reads when it runs on {@code today}: the first day of the month
     * {@link #lookback()} before it.
     */
    public LocalDate firstDay(LocalDate today) {
        return today.minus(lookback).withDayOfMonth(1);
    }
}
