package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyMetrics;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommunityEnergyMetricsResponseTest {

    private static final EnergyMetricsPeriod PERIOD = new EnergyMetricsPeriod(
            OffsetDateTime.parse("2026-05-10T00:00:00+02:00"), OffsetDateTime.parse("2026-05-11T00:00:00+02:00"));

    private final Plant north = PlantMother.random(SupplyMother.random().build()).withTotalPower(100.0).build();
    private final Plant south = PlantMother.random(SupplyMother.random().build()).withTotalPower(50.0).build();

    @Test
    @DisplayName("EVL-001 every kWh figure is rounded to 3 decimals, and the shares are not rounded")
    void everyKWhFigureIsRoundedAndTheSharesAreNot() {
        CommunityEnergyMetrics metrics = CommunityEnergyMetrics.of(PERIOD, 0.1234 + 0.2222, 1.0005, null, 3, 2,
                List.of(north, south), Map.of(north.getSupply().getCode(), 2.0004));

        CommunityEnergyMetricsResponse response = new CommunityEnergyMetricsResponse(metrics);

        assertEquals(0.346, response.getEnergy().getSelfConsumptionKWh());
        assertEquals(1.001, response.getEnergy().getSurplusKWh());
        assertEquals(2.0, response.getEnergy().getProductionKWh());
        assertEquals(metrics.getSelfConsumptionRatio(), response.getSelfConsumptionRatio());
        assertEquals(metrics.getSurplusRatio(), response.getSurplusRatio());
        assertEquals(2.0, response.getPlants().get(0).getProductionKWh());
        assertEquals(1.0, response.getPlants().get(0).getProductionShare());
        assertEquals(150.0, response.getInstalledPowerKWp());
        assertEquals(3, response.getCoverage().getSupplyCount());
        assertEquals(2, response.getCoverage().getSuppliesWithData());
        assertEquals(PERIOD.getStartDate(), response.getPeriod().getStartDate());
        assertEquals(PERIOD.getEndDate(), response.getPeriod().getEndDate());
    }

    @Test
    @DisplayName("CEM-005 a plant is referenced by its id and name, and a gap stays null")
    void aPlantIsReferencedByItsIdAndNameAndAGapStaysNull() {
        CommunityEnergyMetrics metrics = CommunityEnergyMetrics.of(PERIOD, 1d, 1d, null, 1, 1, List.of(north, south),
                Map.of(north.getSupply().getCode(), 3d));

        CommunityPlantEnergyResponse plant = new CommunityEnergyMetricsResponse(metrics).getPlants().get(1);

        assertEquals(south.getId(), plant.getPlant().getId());
        assertEquals(south.getName(), plant.getPlant().getName());
        assertEquals(50.0, plant.getInstalledPowerKWp());
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
}
