package org.lucoenergia.conluz.domain.admin.supply.tariff;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable value object binding a {@link TariffPlan} to a specific
 * {@link DateRange}, together with the electricity tax and VAT rates that apply
 * and the {@link TariffSource} indicating whether the data is real or estimated.
 *
 * <p>It is the building block of a {@link TariffSchedule}: a schedule is a
 * sequence of these segments, each answering "which plan, at which taxes, from
 * which source, over which dates".
 */
public class TariffSegment {

    private final DateRange range;
    private final TariffPlan plan;
    /**
     * VAT rate applied on top of the plan's price per kWh, as a fraction rather than a
     * percentage: {@code 0.21} is 21%, and {@code 0} means no VAT.
     */
    private final BigDecimal vatRate;
    /**
     * Electricity tax rate (Impuesto Especial sobre la Electricidad) applied on the plan's price per
     * kWh before VAT, as a fraction rather than a percentage: {@code 0.0511269632} is 5.11269632%, and
     * {@code 0} means no electricity tax.
     */
    private final BigDecimal electricityTaxRate;
    private final TariffSource source;

    public TariffSegment(DateRange range, TariffPlan plan, BigDecimal vatRate, BigDecimal electricityTaxRate,
                         TariffSource source) {
        this.range = range;
        this.plan = plan;
        this.vatRate = vatRate;
        this.electricityTaxRate = electricityTaxRate;
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

    public BigDecimal getElectricityTaxRate() {
        return electricityTaxRate;
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
                && Objects.equals(electricityTaxRate, that.electricityTaxRate)
                && source == that.source;
    }

    @Override
    public int hashCode() {
        return Objects.hash(range, plan, vatRate, electricityTaxRate, source);
    }

    @Override
    public String toString() {
        return "TariffSegment{" +
                "range=" + range +
                ", plan=" + plan +
                ", vatRate=" + vatRate +
                ", electricityTaxRate=" + electricityTaxRate +
                ", source=" + source +
                '}';
    }
}
