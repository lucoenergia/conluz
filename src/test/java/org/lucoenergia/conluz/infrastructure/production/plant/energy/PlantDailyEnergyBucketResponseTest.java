package org.lucoenergia.conluz.infrastructure.production.plant.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.datadis.get.ProductionAggregate;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergySeriesBucket;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlantDailyEnergyBucketResponseTest {

    @Test
    @DisplayName("PEM-003 a day without records has null production and no hour with data")
    void aDayWithoutRecordsHasNullProduction() {
        PlantDailyEnergyBucketResponse response = new PlantDailyEnergyBucketResponse(PlantEnergySeriesBucket.of(
                OffsetDateTime.parse("2026-10-25T00:00:00+02:00"), 25, Optional.empty()));

        assertEquals("2026/10/25", response.getDate());
        assertEquals("00:00", response.getTime());
        assertNull(response.getProductionKWh());
        assertEquals(0, response.getHoursWithData());
        assertEquals(25, response.getExpectedHours());
    }
}
