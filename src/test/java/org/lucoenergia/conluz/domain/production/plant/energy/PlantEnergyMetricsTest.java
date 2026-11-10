package org.lucoenergia.conluz.domain.production.plant.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.production.datadis.get.ProductionAggregate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlantEnergyMetricsTest {

    private static final EnergyMetricsPeriod MARCH_DST_DAY =
            EnergyMetricsPeriod.ofDay(LocalDate.of(2026, 3, 29), ZoneId.of("Europe/Madrid"));

    @Test
    @DisplayName("PEM-002 a stored zero is a measured zero, not a gap")
    void aStoredZeroIsAMeasuredZero() {
        PlantEnergyMetrics metrics = PlantEnergyMetrics.of(MARCH_DST_DAY, Optional.of(new ProductionAggregate(0d, 23)));

        assertEquals(0d, metrics.getProductionKWh());
        assertEquals(23, metrics.getHoursWithData());
        assertEquals(23, metrics.getExpectedHours());
    }

    @Test
    @DisplayName("PEM-002 a period without records has null production")
    void aPeriodWithoutRecordsHasNullProduction() {
        PlantEnergyMetrics metrics = PlantEnergyMetrics.of(MARCH_DST_DAY, Optional.empty());

        assertNull(metrics.getProductionKWh());
        assertEquals(0, metrics.getHoursWithData());
        assertEquals(23, metrics.getExpectedHours());
    }
}
