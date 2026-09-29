package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.EstimatedPrice;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EstimatedPriceResponseTest {

    /**
     * AC8 (#315). An absent price is an absent object, never an object holding a null.
     */
    @Test
    void anAbsentPriceYieldsNoObjectAtAll() {
        assertNull(EstimatedPriceResponse.from(null));
    }

    @Test
    void theObjectCannotBeBuiltWithoutAPrice() {
        assertThrows(NullPointerException.class, () -> new EstimatedPriceResponse(null));
    }

    /**
     * A rate, not an amount: it is not rounded to cents like the amounts beside it.
     */
    @Test
    void thePriceIsReturnedExactlyAsGiven() {
        EstimatedPriceResponse response = EstimatedPriceResponse.from(EstimatedPrice.of(new BigDecimal("0.1234")));

        assertEquals("0.1234", response.getEurPerKWh().toPlainString());
    }
}
