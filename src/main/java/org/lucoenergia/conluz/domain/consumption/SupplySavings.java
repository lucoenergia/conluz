package org.lucoenergia.conluz.domain.consumption;

import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;

import java.math.BigDecimal;
import java.util.List;
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
 *
 * <p>{@link #getEstimatedPrice()} is present only when the estimated price was used to price at
 * least part of the amount, so a caller can show what an estimate was based on. It is absent both
 * when every part was priced with a contracted tariff and when nothing was priced with the estimate
 * at all -- which is why an {@code ESTIMATE} source may come without one, but a present estimated
 * price always comes with an {@code ESTIMATE} source.
 */
public class SupplySavings {

    private final BigDecimal amountEur;
    private final TariffSource tariffSource;
    private final EstimatedPrice estimatedPrice;

    private SupplySavings(BigDecimal amountEur, TariffSource tariffSource, EstimatedPrice estimatedPrice) {
        if (estimatedPrice != null && tariffSource != TariffSource.ESTIMATE) {
            throw new IllegalArgumentException(
                    "An estimated price can only accompany an ESTIMATE source, not " + tariffSource);
        }
        this.amountEur = amountEur;
        this.tariffSource = tariffSource;
        this.estimatedPrice = estimatedPrice;
    }

    /**
     * @param estimatedPrice the estimated price the amount was partly or wholly priced with, or
     *                       null when the estimate priced no part of it
     */
    public static SupplySavings of(BigDecimal amountEur, TariffSource tariffSource,
                                   EstimatedPrice estimatedPrice) {
        return new SupplySavings(Objects.requireNonNull(amountEur),
                Objects.requireNonNull(tariffSource), estimatedPrice);
    }

    /**
     * The savings of a supply whose period could not be resolved at all -- it has never stored a
     * consumption record and the request did not bound the period itself. There is nothing to
     * price and no period to price it over, so the amount is absent rather than zero: zero would
     * claim the self-consumed energy was worth nothing, which is a different statement.
     *
     * <p>The source is fixed at {@link TariffSource#ESTIMATE} because no tariff is resolved on
     * this path -- there is no {@code DateRange} to ask a resolver for. For the same reason there
     * is no estimated price: nothing was priced with it. {@code ESTIMATE} is the
     * conservative of the two: reporting {@code REAL_TARIFF} for a figure no contracted tariff
     * was ever consulted for is the one direction that actively misleads. The consequence is that
     * a supply on a real tariff with no stored record still reports {@code ESTIMATE}; see the
     * follow-ups in the pull request that introduced this.
     */
    public static SupplySavings unpriced() {
        return new SupplySavings(null, TariffSource.ESTIMATE, null);
    }

    /**
     * The savings of several supplies over one same period, summed without rounding in between.
     * Every part must carry an amount: each one was priced over a resolved period.
     *
     * <p>No parts at all is a total of zero, not an unknown amount: the period exists and nothing
     * in it was priced. That is a different statement from {@link #unpriced()}, which is reserved
     * for the case where there is no period at all.
     *
     * <p>The source aggregates the same way it does within one supply: any estimated part makes
     * the whole total an estimate, since a total is only as trustworthy as its least trustworthy
     * part. With no parts to ask, it falls back to {@code ESTIMATE}, matching {@link #unpriced()}
     * -- claiming a real tariff for a figure no tariff was consulted for is the one direction that
     * actively misleads.
     *
     * <p>The estimated price is the one reported by any part priced partly or wholly with the
     * estimate, and is absent when none was. Every supply's estimated price is read from the same
     * global configuration, so the first one found stands for all of them.
     */
    public static SupplySavings total(List<SupplySavings> parts) {
        BigDecimal amount = BigDecimal.ZERO;
        boolean anyEstimated = parts.isEmpty();
        EstimatedPrice estimatedPrice = null;
        for (SupplySavings part : parts) {
            amount = amount.add(part.getAmountEur());
            anyEstimated = anyEstimated || part.getTariffSource() == TariffSource.ESTIMATE;
            if (estimatedPrice == null) {
                estimatedPrice = part.getEstimatedPrice();
            }
        }
        return of(amount, anyEstimated ? TariffSource.ESTIMATE : TariffSource.REAL_TARIFF, estimatedPrice);
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

    /**
     * The estimated price behind the amount, or null when the estimate priced no part of it.
     */
    public EstimatedPrice getEstimatedPrice() {
        return estimatedPrice;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SupplySavings)) return false;
        SupplySavings that = (SupplySavings) o;
        // compareTo, not equals: BigDecimal.equals distinguishes 0 from 0.00, which is a
        // difference in scale rather than in amount.
        return tariffSource == that.tariffSource
                && Objects.equals(estimatedPrice, that.estimatedPrice)
                && (amountEur == null
                        ? that.amountEur == null
                        : that.amountEur != null && amountEur.compareTo(that.amountEur) == 0);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amountEur == null ? null : amountEur.stripTrailingZeros(), tariffSource,
                estimatedPrice);
    }

    @Override
    public String toString() {
        return "SupplySavings{" +
                "amountEur=" + amountEur +
                ", tariffSource=" + tariffSource +
                ", estimatedPrice=" + estimatedPrice +
                '}';
    }
}
