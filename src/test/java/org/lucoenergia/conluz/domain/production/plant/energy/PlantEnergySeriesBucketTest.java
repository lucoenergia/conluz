package org.lucoenergia.conluz.domain.production.plant.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.datadis.get.ProductionAggregate;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlantEnergySeriesBucketTest {

    private static final OffsetDateTime DAY = OffsetDateTime.parse("2026-11-08T00:00:00+01:00");

    @Test
    @DisplayName("PEM-003 a bucket of stored zeros is a measured zero")
    void aBucketOfStoredZerosIsAMeasuredZero() {
        PlantEnergySeriesBucket bucket = PlantEnergySeriesBucket.of(DAY, 24, Optional.of(new ProductionAggregate(0d, 15)));

        assertEquals(0d, bucket.getProductionKWh());
        assertEquals(15, bucket.getHoursWithData());
        assertEquals(24, bucket.getExpectedHours());
    }

    @Test
    @DisplayName("PEM-003 a bucket without records is a gap")
    void aBucketWithoutRecordsIsAGap() {
        PlantEnergySeriesBucket bucket = PlantEnergySeriesBucket.of(DAY, 24, Optional.empty());

        assertEquals(DAY, bucket.getStartsAt());
        assertNull(bucket.getProductionKWh());
        assertEquals(0, bucket.getHoursWithData());
        assertEquals(24, bucket.getExpectedHours());
    }
}
