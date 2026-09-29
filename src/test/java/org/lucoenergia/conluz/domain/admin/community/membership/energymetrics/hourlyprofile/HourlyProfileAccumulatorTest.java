package org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.hourlyprofile;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.HourlyEnergyRecord;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins the averaging rules of the hourly profile on records alone. How supplies and periods feed
 * it is covered by the service and the endpoint tests.
 */
class HourlyProfileAccumulatorTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final HourlyProfileAccumulator accumulator = new HourlyProfileAccumulator(MADRID);

    @Test
    void thereAreAlwaysTwentyFourBucketsOrderedByHourEvenWithoutAnyRecord() {
        List<HourlyProfileBucket> buckets = accumulator.getBuckets();

        assertEquals(IntStream.range(0, 24).boxed().toList(),
                buckets.stream().map(HourlyProfileBucket::getHour).toList());
    }

    @Test
    void anHourWithoutAnyRecordHasNullAveragesAndNoSamples() {
        add("2026-07-15T08:00:00Z", 1d, 1d, 1d); // local 10:00

        HourlyProfileBucket empty = accumulator.getBuckets().get(11);

        assertNull(empty.getAverageConsumptionKWh());
        assertEquals(0L, empty.getConsumptionSampleCount());
        assertNull(empty.getAverageAssignedProductionKWh());
        assertEquals(0L, empty.getAssignedProductionSampleCount());
    }

    /**
     * Consumption is the grid import plus the self-consumed energy; assigned production is the
     * self-consumed plus the surplus energy. Each divides by the records found, not by the days.
     */
    @Test
    void eachAverageIsTheSumOfItsSamplesDividedByTheirCount() {
        add("2026-07-01T08:00:00Z", 6d, 4d, 2d);   // consumption 10, assigned 6
        add("2026-07-02T08:00:00Z", 1d, 1d, 1d);   // consumption 2, assigned 2
        add("2026-07-05T08:00:00Z", 0.5d, 0d, 3d); // consumption 0.5, assigned 3

        HourlyProfileBucket ten = accumulator.getBuckets().get(10);

        assertEquals(12.5d / 3, ten.getAverageConsumptionKWh(), 1e-9);
        assertEquals(3L, ten.getConsumptionSampleCount());
        assertEquals(11d / 3, ten.getAverageAssignedProductionKWh(), 1e-9);
        assertEquals(3L, ten.getAssignedProductionSampleCount());
    }

    @Test
    void storedZerosOfAssignedProductionAreSamplesThatAverageZero() {
        add("2026-07-01T00:00:00Z", 0.3d, 0d, 0d); // local 02:00
        add("2026-07-02T00:00:00Z", 0.5d, 0d, 0d);

        HourlyProfileBucket night = accumulator.getBuckets().get(2);

        assertEquals(0d, night.getAverageAssignedProductionKWh());
        assertEquals(2L, night.getAssignedProductionSampleCount());
    }

    @Test
    void recordsWithoutAnyAssignedProductionFieldCountOnlyAsConsumptionSamples() {
        add("2026-07-01T08:00:00Z", 2d, null, null);
        add("2026-07-02T08:00:00Z", 4d, null, null);

        HourlyProfileBucket ten = accumulator.getBuckets().get(10);

        assertEquals(3d, ten.getAverageConsumptionKWh(), 1e-9);
        assertEquals(2L, ten.getConsumptionSampleCount());
        assertNull(ten.getAverageAssignedProductionKWh());
        assertEquals(0L, ten.getAssignedProductionSampleCount());
    }

    /**
     * Either assigned field makes a sample, the missing one counting as zero, and a record without
     * self-consumption still has a consumption of its grid import alone.
     */
    @Test
    void aSingleAssignedFieldIsEnoughForASample() {
        add("2026-07-01T08:00:00Z", 1d, null, 5d);
        add("2026-07-02T08:00:00Z", 1d, 3d, null);

        HourlyProfileBucket ten = accumulator.getBuckets().get(10);

        assertEquals(4d, ten.getAverageAssignedProductionKWh(), 1e-9);
        assertEquals(2L, ten.getAssignedProductionSampleCount());
        assertEquals(2.5d, ten.getAverageConsumptionKWh(), 1e-9);
    }

    @Test
    void aRecordFallsInTheBucketOfItsLocalHourNotOfItsUtcHour() {
        add("2026-07-15T10:00:00Z", 7d, 1d, 1d);  // summer time: local 12:00
        add("2026-01-15T10:00:00Z", 9d, 1d, 1d);  // winter time: local 11:00

        List<HourlyProfileBucket> buckets = accumulator.getBuckets();

        assertEquals(0L, buckets.get(10).getConsumptionSampleCount());
        assertEquals(8d, buckets.get(12).getAverageConsumptionKWh(), 1e-9);
        assertEquals(10d, buckets.get(11).getAverageConsumptionKWh(), 1e-9);
    }

    /**
     * On 2026-10-25 local 02:00 happens twice, at 00:00Z (CEST) and at 01:00Z (CET).
     */
    @Test
    void bothRecordsOfTheHourRepeatedByTheOctoberTransitionFallInItsBucket() {
        add("2026-10-25T00:00:00Z", 1d, null, null);
        add("2026-10-25T01:00:00Z", 3d, null, null);
        add("2026-10-25T02:00:00Z", 5d, null, null);

        List<HourlyProfileBucket> buckets = accumulator.getBuckets();

        assertEquals(2L, buckets.get(2).getConsumptionSampleCount());
        assertEquals(2d, buckets.get(2).getAverageConsumptionKWh(), 1e-9);
        assertEquals(1L, buckets.get(3).getConsumptionSampleCount());
        assertEquals(5d, buckets.get(3).getAverageConsumptionKWh(), 1e-9);
    }

    /**
     * On 2026-03-29 local time jumps from 02:00 to 03:00, so 00:00Z is local 01:00 and 01:00Z is
     * local 03:00: local 02:00 gets nothing and 03:00 is not shifted.
     */
    @Test
    void theHourSkippedByTheMarchTransitionReceivesNoRecordAndNoBucketShifts() {
        add("2026-03-29T00:00:00Z", 1d, null, null);
        add("2026-03-29T01:00:00Z", 3d, null, null);
        add("2026-03-29T02:00:00Z", 5d, null, null);

        List<HourlyProfileBucket> buckets = accumulator.getBuckets();

        assertEquals(1d, buckets.get(1).getAverageConsumptionKWh(), 1e-9);
        assertEquals(0L, buckets.get(2).getConsumptionSampleCount());
        assertNull(buckets.get(2).getAverageConsumptionKWh());
        assertEquals(3d, buckets.get(3).getAverageConsumptionKWh(), 1e-9);
        assertEquals(5d, buckets.get(4).getAverageConsumptionKWh(), 1e-9);
    }

    private void add(String time, Double gridImportKWh, Double selfConsumptionKWh, Double surplusKWh) {
        accumulator.add(new HourlyEnergyRecord(Instant.parse(time), gridImportKWh, selfConsumptionKWh, surplusKWh));
    }
}
