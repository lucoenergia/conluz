package org.lucoenergia.conluz.domain.admin.supply.tariff;

import org.lucoenergia.conluz.domain.admin.supply.tariff.InvalidTaxRatePeriodsException.Reason;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Immutable timeline of the rates of one tax, with the rate to apply to a date no period covers.
 *
 * <p>The periods are ordered and contiguous: each starts where its predecessor ends, and only the last
 * may be open-ended. Every rate, the default included, is a fraction in {@code [0, 1)}; a rate of 1 or
 * above only happens when a percentage was written where a fraction was expected. A timeline that
 * breaks any of these rules is rejected at construction (TRF-004), so a wrong configuration stops the
 * application instead of pricing a plausible-looking wrong amount.
 *
 * <p>The list may be empty: every date is then uncovered and priced at the default rate.
 */
public class TaxRatePeriods {

    private final List<TaxRatePeriod> periods;
    private final BigDecimal defaultRate;

    public TaxRatePeriods(List<TaxRatePeriod> periods, BigDecimal defaultRate) {
        for (int i = 0; i < periods.size(); i++) {
            assertValid(periods, i);
        }
        if (defaultRate == null) {
            throw new InvalidTaxRatePeriodsException(Reason.DEFAULT_RATE_MISSING, null);
        }
        if (!isInRange(defaultRate)) {
            throw new InvalidTaxRatePeriodsException(Reason.DEFAULT_RATE_OUT_OF_RANGE, null);
        }
        this.periods = List.copyOf(periods);
        this.defaultRate = defaultRate;
    }

    /**
     * Checks period {@code index} on its own, then against its predecessor, so the first violation in
     * the order of the configuration is the one reported.
     */
    private static void assertValid(List<TaxRatePeriod> periods, int index) {
        TaxRatePeriod period = periods.get(index);
        if (period.from() == null) {
            throw new InvalidTaxRatePeriodsException(Reason.FROM_MISSING, index);
        }
        if (period.rate() == null) {
            throw new InvalidTaxRatePeriodsException(Reason.RATE_MISSING, index);
        }
        if (period.to() != null && !period.to().isAfter(period.from())) {
            throw new InvalidTaxRatePeriodsException(Reason.PERIOD_EMPTY, index);
        }
        if (!isInRange(period.rate())) {
            throw new InvalidTaxRatePeriodsException(Reason.RATE_OUT_OF_RANGE, index);
        }
        if (period.to() == null && index < periods.size() - 1) {
            throw new InvalidTaxRatePeriodsException(Reason.OPEN_ENDED_NOT_LAST, index);
        }
        if (index == 0) {
            return;
        }
        TaxRatePeriod previous = periods.get(index - 1);
        if (period.from().isBefore(previous.from())) {
            throw new InvalidTaxRatePeriodsException(Reason.UNORDERED, index);
        }
        if (period.from().isBefore(previous.to())) {
            throw new InvalidTaxRatePeriodsException(Reason.OVERLAP, index);
        }
        if (period.from().isAfter(previous.to())) {
            throw new InvalidTaxRatePeriodsException(Reason.GAP, index);
        }
    }

    private static boolean isInRange(BigDecimal rate) {
        return rate.signum() >= 0 && rate.compareTo(BigDecimal.ONE) < 0;
    }

    /**
     * The rate in force on {@code date}: the rate of the period covering it, or the default rate when
     * none does.
     */
    public BigDecimal rateOn(LocalDate date) {
        for (TaxRatePeriod period : periods) {
            if (!date.isBefore(period.from()) && (period.to() == null || date.isBefore(period.to()))) {
                return period.rate();
            }
        }
        return defaultRate;
    }

    /**
     * Every date strictly inside {@code range} on which the rate may change: the start or end of a
     * period. The range's own bounds are not included.
     */
    public SortedSet<LocalDate> boundariesWithin(DateRange range) {
        SortedSet<LocalDate> boundaries = new TreeSet<>();
        for (TaxRatePeriod period : periods) {
            addIfInside(boundaries, period.from(), range);
            addIfInside(boundaries, period.to(), range);
        }
        return boundaries;
    }

    private static void addIfInside(SortedSet<LocalDate> boundaries, LocalDate date, DateRange range) {
        if (date != null && date.isAfter(range.getStart()) && date.isBefore(range.getEnd())) {
            boundaries.add(date);
        }
    }

    /**
     * The parts of {@code range} no period covers, in order: the part before the first period starts,
     * and the part after the last one ends, if it ends. With no period at all, the whole range.
     */
    public List<DateRange> uncoveredWithin(DateRange range) {
        if (periods.isEmpty()) {
            return List.of(range);
        }
        List<DateRange> uncovered = new ArrayList<>();
        LocalDate firstFrom = periods.get(0).from();
        if (range.getStart().isBefore(firstFrom)) {
            uncovered.add(new DateRange(range.getStart(), earliest(range.getEnd(), firstFrom)));
        }
        LocalDate lastTo = periods.get(periods.size() - 1).to();
        if (lastTo != null && range.getEnd().isAfter(lastTo)) {
            uncovered.add(new DateRange(latest(range.getStart(), lastTo), range.getEnd()));
        }
        return uncovered;
    }

    private static LocalDate earliest(LocalDate left, LocalDate right) {
        return left.isBefore(right) ? left : right;
    }

    private static LocalDate latest(LocalDate left, LocalDate right) {
        return left.isAfter(right) ? left : right;
    }

    public List<TaxRatePeriod> getPeriods() {
        return periods;
    }

    public BigDecimal getDefaultRate() {
        return defaultRate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TaxRatePeriods)) return false;
        TaxRatePeriods that = (TaxRatePeriods) o;
        return Objects.equals(periods, that.periods) && Objects.equals(defaultRate, that.defaultRate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(periods, defaultRate);
    }

    @Override
    public String toString() {
        return "TaxRatePeriods{" +
                "periods=" + periods +
                ", defaultRate=" + defaultRate +
                '}';
    }
}
