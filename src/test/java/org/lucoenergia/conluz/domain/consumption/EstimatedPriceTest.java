package org.lucoenergia.conluz.domain.consumption;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EstimatedPriceTest {

    /**
     * Nullability lives on the object alone: an estimated price that exists always holds a value,
     * so a caller never has to handle an object that says "estimated" without saying how much.
     */
    @Test
    void anEstimatedPriceCannotHoldANullPrice() {
        assertThrows(NullPointerException.class, () -> EstimatedPrice.of(null));
    }

    /**
     * A rate is not an amount: it keeps every decimal it was configured with.
     */
    @Test
    void thePriceIsKeptExactlyAsGiven() {
        EstimatedPrice price = EstimatedPrice.of(new BigDecimal("0.1234"));

        assertEquals("0.1234", price.getEurPerKWh().toPlainString());
    }

    @Test
    void pricesDifferingOnlyInScaleAreEqual() {
        EstimatedPrice a = EstimatedPrice.of(new BigDecimal("0.15"));
        EstimatedPrice b = EstimatedPrice.of(new BigDecimal("0.150"));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentPricesAreNotEqual() {
        assertNotEquals(EstimatedPrice.of(new BigDecimal("0.15")), EstimatedPrice.of(new BigDecimal("0.16")));
    }
}
