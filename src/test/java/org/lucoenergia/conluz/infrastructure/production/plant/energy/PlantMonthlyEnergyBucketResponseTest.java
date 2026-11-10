package org.lucoenergia.conluz.infrastructure.production.plant.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.datadis.get.ProductionAggregate;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergySeriesBucket;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlantMonthlyEnergyBucketResponseTest {

    @Test
    @DisplayName("EVL-001 a month's production is rounded once on its unrounded sum")
    void aMonthsProductionIsRoundedOnItsSum() {
        PlantMonthlyEnergyBucketResponse response = new PlantMonthlyEnergyBucketResponse(PlantEnergySeriesBucket.of(
                OffsetDateTime.parse("2026-11-01T00:00:00+01:00"), 720,
                Optional.of(new ProductionAggregate(100.0004 + 0.0004, 480))));

        assertEquals("2026/11/01", response.getDate());
        assertEquals(100.001, response.getProductionKWh());
        assertEquals(480, response.getHoursWithData());
        assertEquals(720, response.getExpectedHours());
    }
}
