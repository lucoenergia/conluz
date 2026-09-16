package org.lucoenergia.conluz.domain.consumption;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;

import java.math.BigDecimal;

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
        SupplySavings savings = SupplySavings.of(new BigDecimal("7.95"), TariffSource.REAL_TARIFF);

        assertEquals(0, new BigDecimal("7.95").compareTo(savings.getAmountEur()));
        assertEquals(TariffSource.REAL_TARIFF, savings.getTariffSource());
    }

    /**
     * A priced amount is always a real amount; a null one can only arrive through
     * {@link SupplySavings#unpriced()}, so that the two cases cannot be confused at a call site.
     */
    @Test
    void aPricedAmountCannotBeNull() {
        assertThrows(NullPointerException.class, () -> SupplySavings.of(null, TariffSource.ESTIMATE));
    }

    @Test
    void aSourceIsAlwaysRequired() {
        assertThrows(NullPointerException.class, () -> SupplySavings.of(BigDecimal.ONE, null));
    }

    /**
     * Scale is presentation, not amount: the same money rounded to a different number of decimals
     * is still the same money.
     */
    @Test
    void equalityComparesAmountsRatherThanScales() {
        assertEquals(SupplySavings.of(new BigDecimal("0.00"), TariffSource.ESTIMATE),
                SupplySavings.of(BigDecimal.ZERO, TariffSource.ESTIMATE));
        assertEquals(SupplySavings.of(new BigDecimal("0.00"), TariffSource.ESTIMATE).hashCode(),
                SupplySavings.of(BigDecimal.ZERO, TariffSource.ESTIMATE).hashCode());
    }

    @Test
    void savingsFromDifferentSourcesAreNotEqual() {
        assertNotEquals(SupplySavings.of(BigDecimal.ONE, TariffSource.ESTIMATE),
                SupplySavings.of(BigDecimal.ONE, TariffSource.REAL_TARIFF));
    }

    @Test
    void anUnpricedAmountIsNotEqualToAZeroOne() {
        assertNotEquals(SupplySavings.unpriced(),
                SupplySavings.of(BigDecimal.ZERO, TariffSource.ESTIMATE));
    }
}
