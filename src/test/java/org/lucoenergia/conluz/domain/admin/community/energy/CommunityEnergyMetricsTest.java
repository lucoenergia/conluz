package org.lucoenergia.conluz.domain.admin.community.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.shared.emission.EmissionFactors;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommunityEnergyMetricsTest {

    private static final double TOLERANCE = 1e-12;
    private static final EnergyMetricsPeriod PERIOD = new EnergyMetricsPeriod(
            OffsetDateTime.parse("2026-05-10T00:00:00+02:00"), OffsetDateTime.parse("2026-05-11T00:00:00+02:00"));

    private final Plant north = plant("ESNORTH0000000000000000F", 100.0, 80.0);
    private final Plant south = plant("ESSOUTH0000000000000000F", 50.5, 50.5);

    @Test
    @DisplayName("CEM-003 AC5 self-consumption 60 and surplus 40 give shares of 0.6 and 0.4")
    void selfConsumptionAndSurplusGiveTheTwoShares() {
        CommunityEnergyMetrics metrics = CommunityEnergyMetrics.of(PERIOD, 60d, 40d, null, null, 2, 2,
                List.of(), Map.of());

        assertEquals(0.6, metrics.getSelfConsumptionRatio(), TOLERANCE);
        assertEquals(0.4, metrics.getSurplusRatio(), TOLERANCE);
    }

    @Test
    @DisplayName("CEM-003 AC5 both shares are null when self-consumption and surplus are both zero")
    void bothSharesAreNullWhenBothFiguresAreZero() {
        CommunityEnergyMetrics metrics = CommunityEnergyMetrics.of(PERIOD, 0d, 0d, null, null, 2, 2,
                List.of(), Map.of());

        assertNull(metrics.getSelfConsumptionRatio());
        assertNull(metrics.getSurplusRatio());
    }

    /**
     * Unrounded 0.1234 and 0.2222: the share is computed from them, not from 0.123 and 0.222.
     */
    @Test
    @DisplayName("CEM-003 EVL-002 the shares are computed from the unrounded figures")
    void theSharesAreComputedFromTheUnroundedFigures() {
        CommunityEnergyMetrics metrics = CommunityEnergyMetrics.of(PERIOD, 0.1234, 0.2222, null, null, 1, 1, List.of(),
                Map.of());

        assertEquals(0.1234 / 0.3456, metrics.getSelfConsumptionRatio(), TOLERANCE);
        assertEquals(1.0, metrics.getSelfConsumptionRatio() + metrics.getSurplusRatio(), TOLERANCE);
    }

    @Test
    @DisplayName("CEM-003 CEM-005 production is the sum of the plants', and each plant has its share of it")
    void productionIsTheSumOfThePlantsAndEachPlantHasItsShare() {
        CommunityEnergyMetrics metrics = CommunityEnergyMetrics.of(PERIOD, 1d, 1d, null, null, 1, 1,
                List.of(north, south), Map.of(code(north), 30d, code(south), 10d));

        assertEquals(40d, metrics.getProductionKWh(), TOLERANCE);
        assertEquals(150.5, metrics.getPeakPowerKWp(), TOLERANCE);
        CommunityPlantEnergy first = metrics.getPlants().get(0);
        assertEquals(north.getId(), first.getPlant().id());
        assertEquals(north.getName(), first.getPlant().name());
        assertEquals(100.0, first.getPeakPowerKWp(), TOLERANCE);
        assertEquals(30d, first.getProductionKWh(), TOLERANCE);
        assertEquals(0.75, first.getProductionShare(), TOLERANCE);
        assertEquals(0.25, metrics.getPlants().get(1).getProductionShare(), TOLERANCE);
    }

    @Test
    @DisplayName("CEM-003 AC10 peak and nominal power are each summed over every plant of the community")
    void peakAndNominalPowerAreEachSummedOverEveryPlant() {
        Plant a = plant("ESPLANTA000000000000000F", 80.0, 60.0);
        Plant b = plant("ESPLANTB000000000000000F", 20.0, 20.0);

        CommunityEnergyMetrics metrics = CommunityEnergyMetrics.of(PERIOD, 1d, 1d, null, null, 2, 2,
                List.of(a, b), Map.of(code(a), 30d, code(b), 10d));

        assertEquals(100.0, metrics.getPeakPowerKWp(), TOLERANCE);
        assertEquals(80.0, metrics.getNominalPowerKW(), TOLERANCE);
    }

    @Test
    @DisplayName("CEM-005 AC11 each plant reports its own peak and nominal power, also without production records")
    void eachPlantReportsItsOwnPeakAndNominalPower() {
        Plant a = plant("ESPLANTA000000000000000F", 80.0, 60.0);
        Plant b = plant("ESPLANTB000000000000000F", 20.0, 20.0);

        CommunityEnergyMetrics metrics = CommunityEnergyMetrics.of(PERIOD, 1d, 1d, null, null, 2, 2,
                List.of(a, b), Map.of(code(a), 30d));

        CommunityPlantEnergy first = metrics.getPlants().get(0);
        assertEquals(a.getId(), first.getPlant().id());
        assertEquals(80.0, first.getPeakPowerKWp(), TOLERANCE);
        assertEquals(60.0, first.getNominalPowerKW(), TOLERANCE);
        CommunityPlantEnergy second = metrics.getPlants().get(1);
        assertEquals(b.getId(), second.getPlant().id());
        assertEquals(20.0, second.getPeakPowerKWp(), TOLERANCE);
        assertEquals(20.0, second.getNominalPowerKW(), TOLERANCE);
        assertNull(second.getProductionKWh());
        assertNull(second.getProductionShare());
    }

    @Test
    @DisplayName("CEM-005 AC6 a plant without production records has null production and share, never 0")
    void aPlantWithoutRecordsHasNullProductionAndShare() {
        CommunityEnergyMetrics metrics = CommunityEnergyMetrics.of(PERIOD, 1d, 1d, null, null, 1, 1,
                List.of(north, south), Map.of(code(north), 30d));

        assertEquals(30d, metrics.getProductionKWh(), TOLERANCE);
        assertEquals(1.0, metrics.getPlants().get(0).getProductionShare(), TOLERANCE);
        assertNull(metrics.getPlants().get(1).getProductionKWh());
        assertNull(metrics.getPlants().get(1).getProductionShare());
    }

    @Test
    @DisplayName("CEM-005 a measured zero stays 0, with no share of a production of zero")
    void aMeasuredZeroStaysZero() {
        CommunityEnergyMetrics metrics = CommunityEnergyMetrics.of(PERIOD, 1d, 1d, null, null, 1, 1,
                List.of(north, south), Map.of(code(north), 0d, code(south), 0d));

        assertEquals(0d, metrics.getProductionKWh(), 0d);
        assertEquals(0d, metrics.getPlants().get(0).getProductionKWh(), 0d);
        assertNull(metrics.getPlants().get(0).getProductionShare());
    }

    @Test
    @DisplayName("CEM-005 production is null when no plant stored a production record")
    void productionIsNullWhenNoPlantStoredARecord() {
        CommunityEnergyMetrics metrics = CommunityEnergyMetrics.of(PERIOD, 1d, 1d, null, null, 1, 1,
                List.of(north, south), Map.of());

        assertNull(metrics.getProductionKWh());
    }

    @Test
    @DisplayName("CEM-001 AC2 without a period every figure is null and the plants keep their peak and nominal power")
    void withoutAPeriodEveryFigureIsNull() {
        CommunityEnergyMetrics metrics = CommunityEnergyMetrics.withoutPeriod(3, List.of(north, south));

        assertNull(metrics.getStartDate());
        assertNull(metrics.getEndDate());
        assertNull(metrics.getProductionKWh());
        assertNull(metrics.getSelfConsumptionKWh());
        assertNull(metrics.getSurplusKWh());
        assertNull(metrics.getSelfConsumptionRatio());
        assertNull(metrics.getSurplusRatio());
        assertEquals(3, metrics.getSupplyCount());
        assertEquals(0, metrics.getSuppliesWithData());
        assertEquals(150.5, metrics.getPeakPowerKWp(), TOLERANCE);
        assertEquals(130.5, metrics.getNominalPowerKW(), TOLERANCE);
        assertEquals(2, metrics.getPlants().size());
        assertEquals(80.0, metrics.getPlants().get(0).getNominalPowerKW(), TOLERANCE);
        assertNull(metrics.getPlants().get(0).getProductionKWh());
    }

    @Test
    @DisplayName("CEM-014 AC5 without a period CO₂ avoided and its factor years are null")
    void withoutAPeriodCo2AvoidedIsNull() {
        CommunityEnergyMetrics metrics = CommunityEnergyMetrics.withoutPeriod(3, List.of(north, south));

        assertNull(metrics.getCo2AvoidedKg());
        assertNull(metrics.getCo2AvoidedFactorYears());
    }

    @Test
    @DisplayName("CEM-011 CEM-013 the CO₂ avoided is reported unrounded, with its factor years")
    void theCo2AvoidedIsReportedUnroundedWithItsFactorYears() {
        AvoidedEmissions avoided = AvoidedEmissions.estimate(Map.of(2024, 0.0001, 2025, 0.0002),
                EmissionFactors.of(Map.of(2024, new BigDecimal("0.2"), 2025, new BigDecimal("0.1"))));

        CommunityEnergyMetrics metrics = CommunityEnergyMetrics.of(PERIOD, 1d, 1d, null, avoided, 1, 1,
                List.of(north, south), Map.of(code(north), 0.0001, code(south), 0.0002));

        assertEquals(0, new BigDecimal("0.00004").compareTo(metrics.getCo2AvoidedKg()));
        assertEquals(List.of(2024, 2025), metrics.getCo2AvoidedFactorYears());
    }

    private static Plant plant(String code, double peakPowerKWp, double nominalPowerKW) {
        return PlantMother.random(SupplyMother.random().withCode(code).build()).withPeakPowerKWp(peakPowerKWp)
                .withNominalPowerKW(nominalPowerKW).build();
    }

    private static String code(Plant plant) {
        return plant.getSupply().getCode();
    }
}
