package org.lucoenergia.conluz.domain.datadis.sync;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

/**
 * The two windows the scheduled Datadis sync re-reads: a short one on most days, and the full one
 * once a week.
 *
 * <p>The single definition of both windows: anything that has to cover the same period as the sync
 * derives it from here rather than restating it.
 *
 * @param daily the window the sync re-reads on any day but Sunday
 * @param full  the window the sync re-reads on Sundays
 */
public record DatadisSyncWindows(DatadisSyncWindow daily, DatadisSyncWindow full) {

    public static final DatadisSyncWindows DEFAULT = new DatadisSyncWindows(
            new DatadisSyncWindow(Period.ofYears(1)),
            new DatadisSyncWindow(Period.ofYears(1)));

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
