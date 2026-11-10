package org.lucoenergia.conluz.infrastructure.production.plant.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.production.datadis.get.ProductionAggregate;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyMetrics;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlantEnergyMetricsResponseTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    @Test
    @DisplayName("EVL-001 the production is rounded to 3 decimals, half-up, at response mapping")
    void theProductionIsRoundedAtResponseMapping() {
        PlantEnergyMetrics metrics = PlantEnergyMetrics.of(
                EnergyMetricsPeriod.ofDay(LocalDate.of(2026, 10, 25), ZONE),
                Optional.of(new ProductionAggregate(12.3455, 25)));

        PlantEnergyMetricsResponse response = new PlantEnergyMetricsResponse(metrics);

        assertEquals(12.346, response.getEnergy().getProductionKWh());
        assertEquals(OffsetDateTime.parse("2026-10-25T00:00:00+02:00"), response.getPeriod().getStartDate());
        assertEquals(OffsetDateTime.parse("2026-10-26T00:00:00+01:00"), response.getPeriod().getEndDate());
        assertEquals(25, response.getCoverage().getHoursWithData());
        assertEquals(25, response.getCoverage().getExpectedHours());
    }

    @Test
    @DisplayName("PEM-001 AC2 without a period the bounds and the production are null and no hour is covered")
    void withoutAPeriodTheBoundsAndTheProductionAreNull() {
        PlantEnergyMetricsResponse response = new PlantEnergyMetricsResponse(PlantEnergyMetrics.withoutPeriod());

        assertNull(response.getPeriod().getStartDate());
        assertNull(response.getPeriod().getEndDate());
        assertNull(response.getEnergy().getProductionKWh());
        assertEquals(0, response.getCoverage().getHoursWithData());
        assertEquals(0, response.getCoverage().getExpectedHours());
    }
}
