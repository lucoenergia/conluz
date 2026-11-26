package org.lucoenergia.conluz.domain.datadis.sync;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

/**
 * The two windows the scheduled Datadis sync re-reads: a short one on most days, because Datadis
 * revises mostly recent months, and the full one once a week, reaching every month Datadis still
 * serves.
 *
 * <p>The single definition of both windows: anything that has to cover the same period as the sync
 * derives it from here rather than restating it.
 *
 * @param daily the window the sync re-reads on any day but Sunday
 * @param full  the window the sync re-reads on Sundays
 */
public record DatadisSyncWindows(DatadisSyncWindow daily, DatadisSyncWindow full) {

    /** How many months before the current one the daily window reaches. */
    public static final int DAILY_LOOKBACK_MONTHS = 3;

    /**
     * How many months before the current one the full window reaches: the oldest month Datadis
     * serves when queried by month.
     */
    public static final int MAX_LOOKBACK_MONTHS = 24;

    public static final DatadisSyncWindows DEFAULT = new DatadisSyncWindows(
            new DatadisSyncWindow(Period.ofMonths(DAILY_LOOKBACK_MONTHS)),
            new DatadisSyncWindow(Period.ofMonths(MAX_LOOKBACK_MONTHS)));

    public DatadisSyncWindows {
        Objects.requireNonNull(daily, "daily");
        Objects.requireNonNull(full, "full");
    }

    /**
     * The window the sync re-reads when it runs on {@code today}: {@link #full()} on Sundays,
     * {@link #daily()} on any other day.
     */
    public DatadisSyncWindow forSyncOn(LocalDate today) {
        return today.getDayOfWeek() == DayOfWeek.SUNDAY ? full : daily;
    }
}
