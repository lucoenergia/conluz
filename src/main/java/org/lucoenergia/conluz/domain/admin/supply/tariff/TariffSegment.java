package org.lucoenergia.conluz.domain.admin.supply.tariff;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable value object binding a {@link TariffPlan} to a specific
 * {@link DateRange}, together with the VAT rate that applies and the
 * {@link TariffSource} indicating whether the data is real or estimated.
 *
 * <p>It is the building block of a {@link TariffSchedule}: a schedule is a
 * sequence of these segments, each answering "which plan, at which VAT, from
 * which source, over which dates".
 */
public class TariffSegment {

    private final DateRange range;
    private final TariffPlan plan;
    private final BigDecimal vatRate;
    private final TariffSource source;

    public TariffSegment(DateRange range, TariffPlan plan, BigDecimal vatRate, TariffSource source) {
        this.range = range;
        this.plan = plan;
        this.vatRate = vatRate;
        this.source = source;
    }

    public DateRange getRange() {
        return range;
    }

    public TariffPlan getPlan() {
        return plan;
    }

    public BigDecimal getVatRate() {
        return vatRate;
    }

    public TariffSource getSource() {
        return source;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TariffSegment)) return false;
        TariffSegment that = (TariffSegment) o;
        return Objects.equals(range, that.range)
                && Objects.equals(plan, that.plan)
                && Objects.equals(vatRate, that.vatRate)
                && source == that.source;
    }

    @Override
    public int hashCode() {
        return Objects.hash(range, plan, vatRate, source);
    }

    @Override
    public String toString() {
        return "TariffSegment{" +
                "range=" + range +
                ", plan=" + plan +
                ", vatRate=" + vatRate +
                ", source=" + source +
                '}';
    }
}
