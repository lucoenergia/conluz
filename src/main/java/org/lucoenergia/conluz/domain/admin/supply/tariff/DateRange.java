package org.lucoenergia.conluz.domain.admin.supply.tariff;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable value object representing a half-open interval of dates
 * ({@code [start, end)}: the start date is included, the end date is excluded).
 *
 * <p>It is the temporal unit used throughout the tariff model to scope a
 * {@link TariffSegment} to a period and to ask a {@link SupplyTariffResolver}
 * for the tariff applicable over a span of time.
 */
public class DateRange {

    private final LocalDate start;
    private final LocalDate end;

    public DateRange(LocalDate start, LocalDate end) {
        this.start = start;
        this.end = end;
    }

    public LocalDate getStart() {
        return start;
    }

    public LocalDate getEnd() {
        return end;
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && date.isBefore(end);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DateRange)) return false;
        DateRange dateRange = (DateRange) o;
        return Objects.equals(start, dateRange.start) && Objects.equals(end, dateRange.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    @Override
    public String toString() {
        return "DateRange{" +
                "start=" + start +
                ", end=" + end +
                '}';
    }
}
