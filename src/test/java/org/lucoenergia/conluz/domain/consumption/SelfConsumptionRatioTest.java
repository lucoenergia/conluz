package org.lucoenergia.conluz.domain.consumption;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SelfConsumptionRatioTest {

    private static final double TOLERANCE = 1e-9;

    @Test
    @DisplayName("ENM-006 ENM-007 ENM-008 AC2 the ratio is computed over the regular supplies, and reports their sums and the plant supplies left out")
    void theRatioIsComputedOverTheRegularSupplies() {
        // Regular: 100 self-consumed of 200 assigned. The plant supply (50 of 50) is left out.
        SelfConsumptionRatio ratio = SelfConsumptionRatio.of(List.of(balance(100d, 100d)), 1);

        assertEquals(SelfConsumptionRatioStatus.AVAILABLE, ratio.getStatus());
        assertEquals(0.5d, ratio.getValue(), TOLERANCE);
        assertEquals(100d, ratio.getSelfConsumptionKWh(), TOLERANCE);
        assertEquals(200d, ratio.getAssignedProductionKWh(), TOLERANCE);
        assertEquals(1, ratio.getExcludedPlantSupplyCount());
    }

    @Test
    @DisplayName("ENM-006 the ratio sums every regular supply first and divides once")
    void theRatioSumsFirstAndDividesOnce() {
        // 1 of 10 and 90 of 100: averaging the two ratios would give 0.55, the sums give 91 / 110.
        SelfConsumptionRatio ratio = SelfConsumptionRatio.of(List.of(balance(1d, 9d), balance(90d, 10d)), 0);

        assertEquals(91d / 110d, ratio.getValue(), TOLERANCE);
        assertEquals(91d, ratio.getSelfConsumptionKWh(), TOLERANCE);
        assertEquals(110d, ratio.getAssignedProductionKWh(), TOLERANCE);
        assertEquals(0, ratio.getExcludedPlantSupplyCount());
    }

    @Test
    @DisplayName("ENM-007 AC3 only plant supplies make the ratio not applicable, with zero energy and the count of plant supplies")
    void onlyPlantSuppliesMakeTheRatioNotApplicable() {
        SelfConsumptionRatio ratio = SelfConsumptionRatio.of(List.of(), 2);

        assertEquals(SelfConsumptionRatioStatus.NOT_APPLICABLE, ratio.getStatus());
        assertNull(ratio.getValue());
        assertEquals(0d, ratio.getSelfConsumptionKWh());
        assertEquals(0d, ratio.getAssignedProductionKWh());
        assertEquals(2, ratio.getExcludedPlantSupplyCount());
    }

    @Test
    @DisplayName("ENM-007 a regular supply with nothing assigned beside a plant supply has nothing assigned, not a non-applicable ratio")
    void aRegularSupplyWithNothingAssignedBesideAPlantSupplyHasNothingAssigned() {
        SelfConsumptionRatio ratio = SelfConsumptionRatio.of(List.of(balance(0d, 0d)), 1);

        assertEquals(SelfConsumptionRatioStatus.NOTHING_ASSIGNED, ratio.getStatus());
        assertNull(ratio.getValue());
        assertEquals(1, ratio.getExcludedPlantSupplyCount());
    }

    @Test
    @DisplayName("ENM-007 no supply at all has nothing assigned")
    void noSupplyAtAllHasNothingAssigned() {
        SelfConsumptionRatio ratio = SelfConsumptionRatio.of(List.of(), 0);

        assertEquals(SelfConsumptionRatioStatus.NOTHING_ASSIGNED, ratio.getStatus());
        assertNull(ratio.getValue());
        assertEquals(0, ratio.getExcludedPlantSupplyCount());
    }

    @Test
    @DisplayName("SEM-003 a plant's own supply makes its ratio not applicable, whatever its energy")
    void aPlantsOwnSupplyMakesItsRatioNotApplicable() {
        SelfConsumptionRatio ratio = SelfConsumptionRatio.ofSupply(balance(50d, 0d), true);

        assertEquals(SelfConsumptionRatioStatus.NOT_APPLICABLE, ratio.getStatus());
        assertNull(ratio.getValue());
        assertEquals(0d, ratio.getSelfConsumptionKWh());
        assertEquals(0d, ratio.getAssignedProductionKWh());
        assertEquals(1, ratio.getExcludedPlantSupplyCount());
    }

    @Test
    @DisplayName("SEM-002 SEM-004 a regular supply that used everything assigned has a ratio of 1, with its own energy as basis")
    void aRegularSupplyThatUsedEverythingHasARatioOfOne() {
        SelfConsumptionRatio ratio = SelfConsumptionRatio.ofSupply(balance(50d, 0d), false);

        assertEquals(SelfConsumptionRatioStatus.AVAILABLE, ratio.getStatus());
        assertEquals(1d, ratio.getValue(), TOLERANCE);
        assertEquals(50d, ratio.getSelfConsumptionKWh(), TOLERANCE);
        assertEquals(50d, ratio.getAssignedProductionKWh(), TOLERANCE);
        assertEquals(0, ratio.getExcludedPlantSupplyCount());
    }

    @Test
    @DisplayName("SEM-002 a regular supply with nothing assigned has nothing assigned")
    void aRegularSupplyWithNothingAssignedHasNothingAssigned() {
        SelfConsumptionRatio ratio = SelfConsumptionRatio.ofSupply(balance(0d, 0d), false);

        assertEquals(SelfConsumptionRatioStatus.NOTHING_ASSIGNED, ratio.getStatus());
        assertNull(ratio.getValue());
        assertEquals(0, ratio.getExcludedPlantSupplyCount());
    }

    private static EnergyBalance balance(double selfConsumptionKWh, double surplusKWh) {
        return new EnergyBalance(5d, selfConsumptionKWh, surplusKWh);
    }
}
