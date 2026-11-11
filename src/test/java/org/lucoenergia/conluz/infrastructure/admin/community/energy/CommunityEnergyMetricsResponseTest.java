package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyMetrics;
import org.lucoenergia.conluz.domain.admin.community.energy.SurplusIncome;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommunityEnergyMetricsResponseTest {

    private static final EnergyMetricsPeriod PERIOD = new EnergyMetricsPeriod(
            OffsetDateTime.parse("2026-05-10T00:00:00+02:00"), OffsetDateTime.parse("2026-05-11T00:00:00+02:00"));

    private static final Instant H1 = Instant.parse("2026-05-10T08:00:00Z");
    private static final Instant H2 = Instant.parse("2026-05-10T09:00:00Z");
    private static final Instant H3 = Instant.parse("2026-05-10T10:00:00Z");

    private final Plant north = PlantMother.random(SupplyMother.random().build()).withPeakPowerKWp(100.0)
            .withNominalPowerKW(80.0).build();
    private final Plant south = PlantMother.random(SupplyMother.random().build()).withPeakPowerKWp(50.0)
            .withNominalPowerKW(40.0).build();

    @Test
    @DisplayName("EVL-001 every kWh figure is rounded to 3 decimals, and the shares are not rounded")
    void everyKWhFigureIsRoundedAndTheSharesAreNot() {
        CommunityEnergyMetrics metrics = CommunityEnergyMetrics.of(PERIOD, 0.1234 + 0.2222, 1.0005, null, null, 3, 2,
                List.of(north, south), Map.of(north.getSupply().getCode(), 2.0004));

        CommunityEnergyMetricsResponse response = new CommunityEnergyMetricsResponse(metrics);

        assertEquals(0.346, response.getEnergy().getSelfConsumptionKWh());
        assertEquals(1.001, response.getEnergy().getSurplusKWh());
        assertEquals(2.0, response.getEnergy().getProductionKWh());
        assertEquals(metrics.getSelfConsumptionRatio(), response.getSelfConsumptionRatio());
        assertEquals(metrics.getSurplusRatio(), response.getSurplusRatio());
        assertEquals(2.0, response.getPlants().get(0).getProductionKWh());
        assertEquals(1.0, response.getPlants().get(0).getProductionShare());
        assertEquals(150.0, response.getPeakPowerKWp());
        assertEquals(120.0, response.getNominalPowerKW());
        assertEquals(3, response.getCoverage().getSupplyCount());
        assertEquals(2, response.getCoverage().getSuppliesWithData());
        assertEquals(PERIOD.getStartDate(), response.getPeriod().getStartDate());
        assertEquals(PERIOD.getEndDate(), response.getPeriod().getEndDate());
    }

    @Test
    @DisplayName("CEM-005 a plant is referenced by its id and name, with its peak and nominal power, and a gap stays null")
    void aPlantIsReferencedByItsIdAndNameAndAGapStaysNull() {
        CommunityEnergyMetrics metrics = CommunityEnergyMetrics.of(PERIOD, 1d, 1d, null, null, 1, 1,
                List.of(north, south), Map.of(north.getSupply().getCode(), 3d));

        CommunityPlantEnergyResponse plant = new CommunityEnergyMetricsResponse(metrics).getPlants().get(1);

        assertEquals(south.getId(), plant.getPlant().getId());
        assertEquals(south.getName(), plant.getPlant().getName());
        assertEquals(50.0, plant.getPeakPowerKWp());
        assertEquals(40.0, plant.getNominalPowerKW());
        assertNull(plant.getProductionKWh());
        assertNull(plant.getProductionShare());
    }

    @Test
    @DisplayName("CEM-001 AC2 without a period the bounds and the figures are null")
    void withoutAPeriodTheBoundsAndTheFiguresAreNull() {
        CommunityEnergyMetricsResponse response = new CommunityEnergyMetricsResponse(
                CommunityEnergyMetrics.withoutPeriod(2, List.of(north)));

        assertNull(response.getPeriod().getStartDate());
        assertNull(response.getPeriod().getEndDate());
        assertNull(response.getEnergy().getProductionKWh());
        assertNull(response.getEnergy().getSelfConsumptionKWh());
        assertNull(response.getEnergy().getSurplusKWh());
        assertNull(response.getSelfConsumptionRatio());
        assertNull(response.getSurplusRatio());
        assertEquals(0, response.getCoverage().getSuppliesWithData());
    }

    /**
     * Three hours of 0.004 € each: 0.012 rounds to 0.01 once, where rounding each hour would give 0.00.
     */
    @Test
    @DisplayName("CEM-008 AC9 the amount is rounded to cents once, after the hours are summed")
    void theAmountIsRoundedToCentsOnce() {
        CommunityEnergyMetricsSurplusIncomeResponse response = incomeOf(
                Map.of(H1, 0.1, H2, 0.1, H3, 0.1), Map.of(H1, 0.04, H2, 0.04, H3, 0.04));

        assertEquals(new BigDecimal("0.01"), response.getAmountEur());
    }

    @Test
    @DisplayName("CEM-008 the amount is rounded half up, away from zero for a negative amount")
    void theAmountIsRoundedHalfUp() {
        assertEquals(new BigDecimal("0.13"), incomeOf(Map.of(H1, 2.5), Map.of(H1, 0.05)).getAmountEur());
        assertEquals(new BigDecimal("-0.13"), incomeOf(Map.of(H1, 2.5), Map.of(H1, -0.05)).getAmountEur());
        assertEquals(new BigDecimal("0.00"), incomeOf(Map.of(H1, 2.5), Map.of(H1, 0d)).getAmountEur());
    }

    @Test
    @DisplayName("EVL-001 CEM-009 the unpriced surplus is rounded to 3 decimals")
    void theUnpricedSurplusIsRoundedToThreeDecimals() {
        CommunityEnergyMetricsSurplusIncomeResponse response = incomeOf(Map.of(H1, 1.0004, H2, 0.0001),
                Map.of());

        assertEquals(1.001, response.getUnpricedSurplusKWh());
    }

    @Test
    @DisplayName("CEM-010 AC6 without a period the amount and the unpriced surplus are null")
    void withoutAPeriodTheIncomeIsNull() {
        CommunityEnergyMetricsSurplusIncomeResponse response = new CommunityEnergyMetricsResponse(
                CommunityEnergyMetrics.withoutPeriod(2, List.of(north))).getSurplusIncome();

        assertNull(response.getAmountEur());
        assertNull(response.getUnpricedSurplusKWh());
    }

    @Test
    @DisplayName("CEM-010 AC6 the amount and the unpriced surplus are null when the surplus is")
    void theIncomeIsNullWhenTheSurplusIs() {
        CommunityEnergyMetricsSurplusIncomeResponse response = new CommunityEnergyMetricsResponse(
                CommunityEnergyMetrics.of(PERIOD, null, null, null, null, 3, 0, List.of(north), Map.of()))
                .getSurplusIncome();

        assertNull(response.getAmountEur());
        assertNull(response.getUnpricedSurplusKWh());
    }

    private CommunityEnergyMetricsSurplusIncomeResponse incomeOf(Map<Instant, Double> surplusByHour,
                                                                Map<Instant, Double> meanPriceByHour) {
        CommunityEnergyMetrics metrics = CommunityEnergyMetrics.of(PERIOD, 1d, 1d,
                SurplusIncome.estimate(surplusByHour, meanPriceByHour), null, 3, 2, List.of(north), Map.of());
        return new CommunityEnergyMetricsResponse(metrics).getSurplusIncome();
    }
}
