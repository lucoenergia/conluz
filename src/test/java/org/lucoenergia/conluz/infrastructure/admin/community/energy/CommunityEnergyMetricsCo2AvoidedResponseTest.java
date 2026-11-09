package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.energy.AvoidedEmissions;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyMetrics;
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

class CommunityEnergyMetricsCo2AvoidedResponseTest {

    private static final EnergyMetricsPeriod PERIOD = new EnergyMetricsPeriod(
            OffsetDateTime.parse("2024-11-01T00:00:00+01:00"), OffsetDateTime.parse("2025-03-01T00:00:00+01:00"));

    private final Plant north = PlantMother.random(SupplyMother.random().build()).build();
    private final Plant south = PlantMother.random(SupplyMother.random().build()).build();

    @Test
    @DisplayName("CEM-011 AC6 the amount is rounded once, after summing: 0.0006 + 0.0006 kg gives 0.001, not 0.002")
    void theAmountIsRoundedOnceAfterSumming() {
        EmissionFactors factors = EmissionFactors.of(Map.of(2024, new BigDecimal("0.0002"),
                2025, new BigDecimal("0.0002")));

        CommunityEnergyMetricsCo2AvoidedResponse response = response(Map.of(2024, 3d, 2025, 3d), factors);

        assertEquals(new BigDecimal("0.001"), response.getAmountKg());
    }

    @Test
    @DisplayName("CEM-011 AC6 the amount is rounded half-up to 3 decimals")
    void theAmountIsRoundedHalfUpToThreeDecimals() {
        EmissionFactors factors = EmissionFactors.of(Map.of(2024, new BigDecimal("0.0001"),
                2025, new BigDecimal("0.0002")));

        // 5 × 0.0001 + 10 × 0.0002 = 0.0025, exactly half-way between 0.002 and 0.003.
        CommunityEnergyMetricsCo2AvoidedResponse response = response(Map.of(2024, 5d, 2025, 10d), factors);

        assertEquals(new BigDecimal("0.003"), response.getAmountKg());
    }

    @Test
    @DisplayName("CEM-011 CEM-013 AC2 170 kg are written with 3 decimals, with the factor years ascending")
    void theAmountAndTheFactorYearsAreReported() {
        EmissionFactors factors = EmissionFactors.of(Map.of(2024, new BigDecimal("0.200"),
                2025, new BigDecimal("0.150")));

        CommunityEnergyMetricsCo2AvoidedResponse response = response(Map.of(2025, 600d, 2024, 400d), factors);

        assertEquals(new BigDecimal("170.000"), response.getAmountKg());
        assertEquals(List.of(2024, 2025), response.getFactorYears());
    }

    @Test
    @DisplayName("CEM-014 AC5 both fields are null without a production record")
    void bothFieldsAreNullWithoutAProductionRecord() {
        CommunityEnergyMetrics metrics = CommunityEnergyMetrics.of(PERIOD, 1d, 1d, null, null, 1, 1,
                List.of(north, south), Map.of());

        CommunityEnergyMetricsCo2AvoidedResponse response = new CommunityEnergyMetricsCo2AvoidedResponse(metrics);

        assertNull(response.getAmountKg());
        assertNull(response.getFactorYears());
    }

    @Test
    @DisplayName("CEM-014 AC5 both fields are null when no period resolves")
    void bothFieldsAreNullWhenNoPeriodResolves() {
        CommunityEnergyMetricsCo2AvoidedResponse response = new CommunityEnergyMetricsResponse(
                CommunityEnergyMetrics.withoutPeriod(2, List.of(north, south))).getCo2Avoided();

        assertNull(response.getAmountKg());
        assertNull(response.getFactorYears());
    }

    private CommunityEnergyMetricsCo2AvoidedResponse response(Map<Integer, Double> productionByYear,
                                                             EmissionFactors factors) {
        double production = productionByYear.values().stream().mapToDouble(Double::doubleValue).sum();
        CommunityEnergyMetrics metrics = CommunityEnergyMetrics.of(PERIOD, 1d, 1d, null,
                AvoidedEmissions.estimate(productionByYear, factors), 1, 1, List.of(north, south),
                Map.of(north.getSupply().getCode(), production));
        return new CommunityEnergyMetricsResponse(metrics).getCo2Avoided();
    }
}
