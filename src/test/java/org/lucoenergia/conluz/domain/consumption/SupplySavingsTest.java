package org.lucoenergia.conluz.domain.consumption;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SupplySavingsTest {

    /**
     * Absent, not zero: zero claims the self-consumed energy was worth nothing, which is a
     * different statement from "there was no period to price".
     */
    @Test
    void unpricedSavingsCarryNoAmount() {
        assertNull(SupplySavings.unpriced().getAmountEur());
    }

    /**
     * No tariff is resolved on the unpriced path, and of the two sources only ESTIMATE is safe to
     * assume: claiming REAL_TARIFF for a figure no contracted tariff was consulted for is the one
     * direction that misleads.
     */
    @Test
    void unpricedSavingsStillDeclareAConservativeSource() {
        assertEquals(TariffSource.ESTIMATE, SupplySavings.unpriced().getTariffSource());
    }

    @Test
    void aPricedAmountTravelsWithItsSource() {
        SupplySavings savings = SupplySavings.of(new BigDecimal("7.95"), TariffSource.REAL_TARIFF, null);

        assertEquals(0, new BigDecimal("7.95").compareTo(savings.getAmountEur()));
        assertEquals(TariffSource.REAL_TARIFF, savings.getTariffSource());
    }

    /**
     * A priced amount is always a real amount; a null one can only arrive through
     * {@link SupplySavings#unpriced()}, so that the two cases cannot be confused at a call site.
     */
    @Test
    void aPricedAmountCannotBeNull() {
        assertThrows(NullPointerException.class, () -> SupplySavings.of(null, TariffSource.ESTIMATE, null));
    }

    @Test
    void aSourceIsAlwaysRequired() {
        assertThrows(NullPointerException.class, () -> SupplySavings.of(BigDecimal.ONE, null, null));
    }

    /**
     * Scale is presentation, not amount: the same money rounded to a different number of decimals
     * is still the same money.
     */
    @Test
    void equalityComparesAmountsRatherThanScales() {
        assertEquals(SupplySavings.of(new BigDecimal("0.00"), TariffSource.ESTIMATE, null),
                SupplySavings.of(BigDecimal.ZERO, TariffSource.ESTIMATE, null));
        assertEquals(SupplySavings.of(new BigDecimal("0.00"), TariffSource.ESTIMATE, null).hashCode(),
                SupplySavings.of(BigDecimal.ZERO, TariffSource.ESTIMATE, null).hashCode());
    }

    @Test
    void savingsFromDifferentSourcesAreNotEqual() {
        assertNotEquals(SupplySavings.of(BigDecimal.ONE, TariffSource.ESTIMATE, null),
                SupplySavings.of(BigDecimal.ONE, TariffSource.REAL_TARIFF, null));
    }

    @Test
    void anUnpricedAmountIsNotEqualToAZeroOne() {
        assertNotEquals(SupplySavings.unpriced(),
                SupplySavings.of(BigDecimal.ZERO, TariffSource.ESTIMATE, null));
    }

    /**
     * The unpriced path consulted no tariff at all, so nothing was priced with the estimate either.
     */
    @Test
    void unpricedSavingsCarryNoEstimatedPrice() {
        assertNull(SupplySavings.unpriced().getEstimatedPrice());
    }

    @Test
    void anEstimatedAmountTravelsWithTheEstimatedPriceBehindIt() {
        EstimatedPrice price = EstimatedPrice.of(new BigDecimal("0.15"));

        SupplySavings savings = SupplySavings.of(new BigDecimal("7.95"), TariffSource.ESTIMATE, price);

        assertEquals(price, savings.getEstimatedPrice());
    }

    /**
     * An ESTIMATE source without a price is legitimate -- it is what a figure no tariff was
     * consulted for reports -- so absence of the price is not tied to the source in that direction.
     */
    @Test
    void anEstimateMayComeWithoutAnEstimatedPrice() {
        assertNull(SupplySavings.of(BigDecimal.ZERO, TariffSource.ESTIMATE, null).getEstimatedPrice());
    }

    /**
     * A figure priced entirely with contracted tariffs was not priced with the estimate, so
     * attaching an estimated price to it would label it with a price it was never computed from.
     */
    @Test
    void aRealTariffAmountCannotCarryAnEstimatedPrice() {
        EstimatedPrice price = EstimatedPrice.of(new BigDecimal("0.15"));

        assertThrows(IllegalArgumentException.class,
                () -> SupplySavings.of(BigDecimal.ONE, TariffSource.REAL_TARIFF, price));
    }

    @Test
    void savingsWithDifferentEstimatedPricesAreNotEqual() {
        assertNotEquals(
                SupplySavings.of(BigDecimal.ONE, TariffSource.ESTIMATE, EstimatedPrice.of(new BigDecimal("0.15"))),
                SupplySavings.of(BigDecimal.ONE, TariffSource.ESTIMATE, EstimatedPrice.of(new BigDecimal("0.16"))));
        assertNotEquals(
                SupplySavings.of(BigDecimal.ONE, TariffSource.ESTIMATE, EstimatedPrice.of(new BigDecimal("0.15"))),
                SupplySavings.of(BigDecimal.ONE, TariffSource.ESTIMATE, null));
    }

    /**
     * Summed unrounded: 0.004 + 0.004 rounded per part would be 0.00 + 0.00, and 0.01 as a whole.
     */
    @Test
    void aTotalAddsTheAmountsOfEveryPartWithoutRoundingThem() {
        SupplySavings total = SupplySavings.total(List.of(
                SupplySavings.of(new BigDecimal("0.004"), TariffSource.REAL_TARIFF, null),
                SupplySavings.of(new BigDecimal("0.004"), TariffSource.REAL_TARIFF, null),
                SupplySavings.of(new BigDecimal("12.5"), TariffSource.REAL_TARIFF, null)));

        assertEquals(0, new BigDecimal("12.508").compareTo(total.getAmountEur()));
    }

    @Test
    void aTotalOfPartsPricedOnlyWithContractedTariffsIsARealTariffAmount() {
        SupplySavings total = SupplySavings.total(List.of(
                SupplySavings.of(BigDecimal.ONE, TariffSource.REAL_TARIFF, null),
                SupplySavings.of(BigDecimal.TEN, TariffSource.REAL_TARIFF, null)));

        assertEquals(TariffSource.REAL_TARIFF, total.getTariffSource());
        assertNull(total.getEstimatedPrice());
    }

    @Test
    void oneEstimatedPartMakesTheWholeTotalAnEstimateCarryingItsPrice() {
        EstimatedPrice price = EstimatedPrice.of(new BigDecimal("0.15"));

        SupplySavings total = SupplySavings.total(List.of(
                SupplySavings.of(BigDecimal.ONE, TariffSource.REAL_TARIFF, null),
                SupplySavings.of(BigDecimal.TEN, TariffSource.ESTIMATE, price),
                SupplySavings.of(BigDecimal.ONE, TariffSource.REAL_TARIFF, null)));

        assertEquals(TariffSource.ESTIMATE, total.getTariffSource());
        assertEquals(price, total.getEstimatedPrice());
    }

    /**
     * Zero, not absent: a total of no parts is a period in which nothing was priced, which is not
     * the same statement as {@link SupplySavings#unpriced()}.
     */
    @Test
    void aTotalOfNoPartsIsAZeroEstimateWithoutAnEstimatedPrice() {
        SupplySavings total = SupplySavings.total(List.of());

        assertEquals(0, BigDecimal.ZERO.compareTo(total.getAmountEur()));
        assertEquals(TariffSource.ESTIMATE, total.getTariffSource());
        assertNull(total.getEstimatedPrice());
    }
}
