package org.lucoenergia.conluz.domain.consumption;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable value object holding the estimated energy-term price, in euros per kWh before taxes,
 * that a savings figure was priced with.
 *
 * <p>It only exists when the estimate was actually used: a figure priced entirely with contracted
 * tariffs, or not priced at all, carries no estimated price rather than one holding a null. That is
 * why the price itself can never be null -- absence is expressed by the absence of the whole object.
 *
 * <p>The price is kept exactly as configured, unrounded: it is a rate, not an amount, and rounding
 * it to cents would misstate what the figures were computed with.
 */
public class EstimatedPrice {

    private final BigDecimal eurPerKWh;

    private EstimatedPrice(BigDecimal eurPerKWh) {
        this.eurPerKWh = eurPerKWh;
    }

    public static EstimatedPrice of(BigDecimal eurPerKWh) {
        return new EstimatedPrice(Objects.requireNonNull(eurPerKWh));
    }

    /**
     * The energy-term price per kWh, before taxes. Never null. Unrounded.
     */
    public BigDecimal getEurPerKWh() {
        return eurPerKWh;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EstimatedPrice)) return false;
        EstimatedPrice that = (EstimatedPrice) o;
        // compareTo, not equals: BigDecimal.equals distinguishes 0.15 from 0.150, which is a
        // difference in scale rather than in price.
        return eurPerKWh.compareTo(that.eurPerKWh) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(eurPerKWh.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return "EstimatedPrice{" +
                "eurPerKWh=" + eurPerKWh +
                '}';
    }
}
