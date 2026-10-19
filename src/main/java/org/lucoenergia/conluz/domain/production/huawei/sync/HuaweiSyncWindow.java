package org.lucoenergia.conluz.domain.production.huawei.sync;

import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * How far back the daily Huawei sync of previous days re-reads hourly records: every day from
 * {@link #lookback()} before today through yesterday, both inclusive. Today itself is synced by
 * its own hourly job.
 *
 * <p>The single definition of that window: anything that has to cover the same period as the sync
 * derives it from here rather than restating it.
 *
 * @param lookback how far back from today the first synced day is
 */
public record HuaweiSyncWindow(Period lookback) {

    public static final HuaweiSyncWindow DEFAULT = new HuaweiSyncWindow(Period.ofDays(7));

    public HuaweiSyncWindow {
        Objects.requireNonNull(lookback, "lookback");
        if (lookback.isNegative()) {
            throw new IllegalArgumentException("The sync window lookback cannot be negative: " + lookback);
        }
    }

    /**
     * The first day the sync re-reads when it runs on {@code today}: {@link #lookback()} before it.
     */
    public LocalDate firstDay(LocalDate today) {
        return today.minus(lookback);
    }

    /**
     * Every month that overlaps {@code [firstDay(today), today]}, oldest first, both ends inclusive.
     * The range ends at today rather than at yesterday, the last day this sync reads, because today
     * is synced too, by its own job.
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
