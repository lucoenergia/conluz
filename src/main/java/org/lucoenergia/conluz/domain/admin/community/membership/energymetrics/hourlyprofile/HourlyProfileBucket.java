package org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.hourlyprofile;

/**
 * One hour of the local day in an average hourly profile: the average consumption and the average
 * assigned production of every record found for that hour, each with the number of records, or
 * samples, it rests on.
 *
 * <p>The two counts are independent, since a record can carry consumption without carrying
 * assigned production. They count records, not days: the local hour a daylight saving fall-back
 * repeats holds two samples of that day, and the hour a spring-forward skips holds none.
 */
public class HourlyProfileBucket {

    private final int hour;
    private final Double averageConsumptionKWh;
    private final long consumptionSampleCount;
    private final Double averageAssignedProductionKWh;
    private final long assignedProductionSampleCount;

    public HourlyProfileBucket(int hour, Double averageConsumptionKWh, long consumptionSampleCount,
                               Double averageAssignedProductionKWh, long assignedProductionSampleCount) {
        this.hour = hour;
        this.averageConsumptionKWh = averageConsumptionKWh;
        this.consumptionSampleCount = consumptionSampleCount;
        this.averageAssignedProductionKWh = averageAssignedProductionKWh;
        this.assignedProductionSampleCount = assignedProductionSampleCount;
    }

    /**
     * An hour without any sample of either series.
     */
    public static HourlyProfileBucket empty(int hour) {
        return new HourlyProfileBucket(hour, null, 0L, null, 0L);
    }

    /**
     * The hour of the local day, from 0 to 23.
     */
    public int getHour() {
        return hour;
    }

    /**
     * The average consumption of the samples found for this hour, or null when there is none.
     */
    public Double getAverageConsumptionKWh() {
        return averageConsumptionKWh;
    }

    public long getConsumptionSampleCount() {
        return consumptionSampleCount;
    }

    /**
     * The average assigned production of the samples found for this hour, or null when there is
     * none. A stored zero is a sample, so an hour whose samples are all zero averages zero.
     */
    public Double getAverageAssignedProductionKWh() {
        return averageAssignedProductionKWh;
    }

    public long getAssignedProductionSampleCount() {
        return assignedProductionSampleCount;
    }
}
