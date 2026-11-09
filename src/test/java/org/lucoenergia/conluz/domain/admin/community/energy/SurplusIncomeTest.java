package org.lucoenergia.conluz.domain.admin.community.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SurplusIncomeTest {

    private static final Instant H1 = Instant.parse("2025-10-10T10:00:00Z");
    private static final Instant H2 = Instant.parse("2025-10-10T11:00:00Z");
    private static final Instant H3 = Instant.parse("2025-10-10T12:00:00Z");
    private static final Instant H4 = Instant.parse("2025-10-10T13:00:00Z");

    /**
     * 5 × 0.07 + 2 × 0.05 + 4 × −0.01 + 3 × 0 = 0.35 + 0.10 − 0.04 + 0 = 0.41.
     */
    @Test
    @DisplayName("CEM-008 AC1 AC3 each hour's surplus is priced at its own mean price, negative and zero prices as they are")
    void eachHoursSurplusIsPricedAtItsOwnMeanPrice() {
        SurplusIncome income = SurplusIncome.estimate(
                Map.of(H1, 5d, H2, 2d, H3, 4d, H4, 3d),
                Map.of(H1, 0.07, H2, 0.05, H3, -0.01, H4, 0d));

        assertEquals(0, new BigDecimal("0.41").compareTo(income.getAmountEur()), () -> "was " + income.getAmountEur());
        assertEquals(0d, income.getUnpricedSurplusKWh());
    }

    @Test
    @DisplayName("CEM-008 AC3 a negative price makes the amount negative")
    void aNegativePriceMakesTheAmountNegative() {
        SurplusIncome income = SurplusIncome.estimate(Map.of(H1, 4d, H2, 1d), Map.of(H1, -0.01, H2, 0.01));

        assertEquals(0, new BigDecimal("-0.03").compareTo(income.getAmountEur()), () -> "was " + income.getAmountEur());
    }

    /**
     * Three hours of 0.004 € each: rounded hour by hour they would add up to 0.00.
     */
    @Test
    @DisplayName("CEM-008 AC9 the amount is summed at full precision, unrounded")
    void theAmountIsSummedUnrounded() {
        SurplusIncome income = SurplusIncome.estimate(Map.of(H1, 0.1, H2, 0.1, H3, 0.1),
                Map.of(H1, 0.04, H2, 0.04, H3, 0.04));

        assertEquals(0, new BigDecimal("0.012").compareTo(income.getAmountEur()), () -> "was " + income.getAmountEur());
    }

    @Test
    @DisplayName("CEM-009 AC4 an hour without a price adds its surplus to the unpriced surplus and nothing to the amount")
    void anHourWithoutAPriceIsUnpriced() {
        SurplusIncome income = SurplusIncome.estimate(Map.of(H1, 5d, H2, 1.5, H3, 0.25),
                Map.of(H1, 0.07, H4, 1000d));

        assertEquals(0, new BigDecimal("0.35").compareTo(income.getAmountEur()), () -> "was " + income.getAmountEur());
        assertEquals(1.75, income.getUnpricedSurplusKWh(), 1e-12);
    }

    @Test
    @DisplayName("CEM-009 CEM-010 AC5 without any hour with surplus the amount and the unpriced surplus are 0")
    void withoutSurplusTheAmountAndTheUnpricedSurplusAreZero() {
        SurplusIncome income = SurplusIncome.estimate(Map.of(), Map.of(H1, 0.07, H2, 0.05));

        assertEquals(0, BigDecimal.ZERO.compareTo(income.getAmountEur()));
        assertEquals(0d, income.getUnpricedSurplusKWh());
    }
}
