package org.lucoenergia.conluz.domain.consumption;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EnergyBalanceTest {

    @Test
    void addingTwoBalancesAddsTheirRawTotals() {
        EnergyBalance sum = new EnergyBalance(60d, 40d, 10d).plus(new EnergyBalance(1d, 1d, 3d));

        assertEquals(61d, sum.getGridImportKWh(), 1e-9);
        assertEquals(41d, sum.getSelfConsumptionKWh(), 1e-9);
        assertEquals(13d, sum.getSurplusKWh(), 1e-9);
        assertEquals(102d, sum.getTotalConsumptionKWh(), 1e-9);
        assertEquals(54d, sum.getAssignedProductionKWh(), 1e-9);
    }

    /**
     * The first balance has a ratio of 0.40, the second 0.50. Averaging them would give 0.45; the
     * ratio of the sum is 41/102.
     */
    @Test
    void theRatioOfASumIsDerivedFromTheSummedTotalsAndNotAveraged() {
        EnergyBalance sum = new EnergyBalance(60d, 40d, 10d).plus(new EnergyBalance(1d, 1d, 3d));

        assertEquals(0.4020d, sum.getSelfSufficiencyRatio(), 1e-4);
    }

    @Test
    void addingAnEmptyBalanceChangesNothing() {
        EnergyBalance balance = new EnergyBalance(60d, 40d, 10d);

        EnergyBalance sum = EnergyBalance.empty().plus(balance);

        assertEquals(balance.getGridImportKWh(), sum.getGridImportKWh());
        assertEquals(balance.getSelfConsumptionKWh(), sum.getSelfConsumptionKWh());
        assertEquals(balance.getSurplusKWh(), sum.getSurplusKWh());
        assertEquals(balance.getSelfSufficiencyRatio(), sum.getSelfSufficiencyRatio());
    }

    @Test
    void anEmptyBalanceHasZeroTotalsAndNoRatios() {
        EnergyBalance empty = EnergyBalance.empty();

        assertEquals(0d, empty.getTotalConsumptionKWh());
        assertEquals(0d, empty.getAssignedProductionKWh());
        assertNull(empty.getSelfSufficiencyRatio());
    }
}
