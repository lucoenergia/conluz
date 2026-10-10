package org.lucoenergia.conluz.domain.datadis.sync;

import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
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

    /**
     * Every month the sync re-reads when it runs on {@code today}, oldest first: from the month of
     * {@link #firstDay(LocalDate)} through the month of {@code today}, both inclusive. A lookback of
     * N months therefore yields N + 1 months; the first one is the month the sync starts in and is
     * never left out.
     */
    public List<YearMonth> months(LocalDate today) {
        YearMonth first = YearMonth.from(firstDay(today));
        YearMonth current = YearMonth.from(today);
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth month = first; !month.isAfter(current); month = month.plusMonths(1)) {
            months.add(month);
        }
        return months;
    }

    /**
     * Every year {@link #months(LocalDate)} overlaps when the sync runs on {@code today}, oldest
     * first.
     */
    public List<Integer> years(LocalDate today) {
        return months(today).stream()
                .map(YearMonth::getYear)
                .distinct()
                .toList();
    }
}
