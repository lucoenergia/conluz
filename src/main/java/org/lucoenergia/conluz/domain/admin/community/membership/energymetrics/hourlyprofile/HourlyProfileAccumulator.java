package org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.hourlyprofile;

import org.lucoenergia.conluz.domain.consumption.datadis.metrics.HourlyEnergyRecord;

import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Folds hourly records, one at a time, into the 24 buckets of an average hourly profile, so the
 * records never need to be held in memory.
 *
 * <p>Each record falls in the bucket of its hour of the day in the given zone. Each series keeps
 * its own sum and its own sample count, and each average divides the sum by that count: by the
 * records actually found, never by the days of the period, and never by averaging per-supply
 * averages. Daylight saving days need no rule of their own: the clock already puts both records
 * of a repeated hour in one bucket, and no record in a skipped one.
 *
 * <ul>
 *     <li>A record is a consumption sample when it carries {@code consumption_kwh}. Its value is
 *     the total consumption: the grid import plus the self-consumed energy, the latter counting as
 *     zero when absent.</li>
 *     <li>A record is an assigned production sample when it carries self-consumed or surplus
 *     energy. Its value is their sum, a missing one counting as zero. A stored zero is a sample:
 *     inside a published month it is a measured zero.</li>
 * </ul>
 *
 * <p>Not thread-safe: records must be added by one thread at a time.
 */
public class HourlyProfileAccumulator {

    public static final int HOURS_OF_DAY = 24;

    private final ZoneId zone;
    private final double[] consumptionSums = new double[HOURS_OF_DAY];
    private final long[] consumptionSampleCounts = new long[HOURS_OF_DAY];
    private final double[] assignedProductionSums = new double[HOURS_OF_DAY];
    private final long[] assignedProductionSampleCounts = new long[HOURS_OF_DAY];

    /**
     * @param zone the zone whose local hour each record is bucketed by
     */
    public HourlyProfileAccumulator(ZoneId zone) {
        this.zone = zone;
    }

    public void add(HourlyEnergyRecord record) {
        int hour = record.getTime().atZone(zone).getHour();
        Double gridImport = record.getGridImportKWh();
        Double selfConsumption = record.getSelfConsumptionKWh();
        Double surplus = record.getSurplusKWh();

        if (gridImport != null) {
            consumptionSums[hour] += gridImport + orZero(selfConsumption);
            consumptionSampleCounts[hour]++;
        }
        if (selfConsumption != null || surplus != null) {
            assignedProductionSums[hour] += orZero(selfConsumption) + orZero(surplus);
            assignedProductionSampleCounts[hour]++;
        }
    }

    /**
     * The 24 buckets, ordered from hour 0 to hour 23, including the hours without any sample.
     */
    public List<HourlyProfileBucket> getBuckets() {
        return IntStream.range(0, HOURS_OF_DAY)
                .mapToObj(hour -> new HourlyProfileBucket(
                        hour,
                        average(consumptionSums[hour], consumptionSampleCounts[hour]),
                        consumptionSampleCounts[hour],
                        average(assignedProductionSums[hour], assignedProductionSampleCounts[hour]),
                        assignedProductionSampleCounts[hour]))
                .toList();
    }

    private static Double average(double sum, long sampleCount) {
        return sampleCount == 0 ? null : sum / sampleCount;
    }

    private static double orZero(Double value) {
        return value == null ? 0d : value;
    }
}
