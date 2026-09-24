package org.lucoenergia.conluz.domain.consumption;

import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable value object holding what a supply's self-consumed energy was worth over a resolved
 * period, together with the origin of the prices it was computed from.
 *
 * <p>The amount is the energy term before tax multiplied by the segment's VAT factor, summed over
 * the tariff segments covering the period. It is unrounded here: rounding is a presentational
 * concern and happens once, where the response is built, so a period cut into several segments is
 * not rounded once per segment.
 *
 * <p>{@link #getTariffSource()} is never null. A monetary figure derived from an estimate and one
 * derived from a contracted tariff are not interchangeable, and a caller has no way to tell them
 * apart unless the figure says so.
 */
public class SupplySavings {

    private final BigDecimal amountEur;
    private final TariffSource tariffSource;

    private SupplySavings(BigDecimal amountEur, TariffSource tariffSource) {
        this.amountEur = amountEur;
        this.tariffSource = tariffSource;
    }

    public static SupplySavings of(BigDecimal amountEur, TariffSource tariffSource) {
        return new SupplySavings(Objects.requireNonNull(amountEur),
                Objects.requireNonNull(tariffSource));
    }

    /**
     * The savings of a supply whose period could not be resolved at all -- it has never stored a
     * consumption record and the request did not bound the period itself. There is nothing to
     * price and no period to price it over, so the amount is absent rather than zero: zero would
     * claim the self-consumed energy was worth nothing, which is a different statement.
     *
     * <p>The source is fixed at {@link TariffSource#ESTIMATE} because no tariff is resolved on
     * this path -- there is no {@code DateRange} to ask a resolver for. {@code ESTIMATE} is the
     * conservative of the two: reporting {@code REAL_TARIFF} for a figure no contracted tariff
     * was ever consulted for is the one direction that actively misleads. The consequence is that
     * a supply on a real tariff with no stored record still reports {@code ESTIMATE}; see the
     * follow-ups in the pull request that introduced this.
     */
    public static SupplySavings unpriced() {
        return new SupplySavings(null, TariffSource.ESTIMATE);
    }

    /**
     * The estimated amount in euros, or null when no period could be resolved. Unrounded.
     */
    public BigDecimal getAmountEur() {
        return amountEur;
    }

    public TariffSource getTariffSource() {
        return tariffSource;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SupplySavings)) return false;
        SupplySavings that = (SupplySavings) o;
        // compareTo, not equals: BigDecimal.equals distinguishes 0 from 0.00, which is a
        // difference in scale rather than in amount.
        return tariffSource == that.tariffSource
                && (amountEur == null
                        ? that.amountEur == null
                        : that.amountEur != null && amountEur.compareTo(that.amountEur) == 0);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amountEur == null ? null : amountEur.stripTrailingZeros(), tariffSource);
    }

    @Override
    public String toString() {
        return "SupplySavings{" +
                "amountEur=" + amountEur +
                ", tariffSource=" + tariffSource +
                '}';
    }
}
