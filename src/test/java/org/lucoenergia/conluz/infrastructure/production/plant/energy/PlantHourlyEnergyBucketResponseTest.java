package org.lucoenergia.conluz.infrastructure.production.plant.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.datadis.get.ProductionAggregate;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergySeriesBucket;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlantHourlyEnergyBucketResponseTest {

    private static final OffsetDateTime REPEATED_HOUR = OffsetDateTime.parse("2026-10-25T02:00:00+01:00");

    @Test
    @DisplayName("EVL-001 an hour's production is rounded at response mapping, keyed by its instant and local time")
    void anHoursProductionIsRoundedAndKeyedByItsInstant() {
        PlantHourlyEnergyBucketResponse response = new PlantHourlyEnergyBucketResponse(
                PlantEnergySeriesBucket.of(REPEATED_HOUR, 1, Optional.of(new ProductionAggregate(1.23456, 1))));

        assertEquals(REPEATED_HOUR, response.getStartsAt());
        assertEquals("2026/10/25", response.getDate());
        assertEquals("02:00", response.getTime());
        assertEquals(1.235, response.getProductionKWh());
        assertEquals(1, response.getHoursWithData());
        assertEquals(1, response.getExpectedHours());
    }
}
