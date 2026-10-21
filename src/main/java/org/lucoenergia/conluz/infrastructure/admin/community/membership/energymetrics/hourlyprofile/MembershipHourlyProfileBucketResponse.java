package org.lucoenergia.conluz.infrastructure.admin.community.membership.energymetrics.hourlyprofile;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.hourlyprofile.HourlyProfileBucket;

/**
 * One hour of the local day: the average total consumption and the average assigned production of
 * the membership's supplies at that hour, each with the number of hourly records, or samples, it
 * rests on. The two counts are independent. Averages and sample counts carry the name of the
 * quantity they are about, as the energy metrics name it (#460).
 */
@Schema(requiredProperties = {"hour", "averageTotalConsumptionKWh", "totalConsumptionSampleCount",
        "averageAssignedProductionKWh", "assignedProductionSampleCount"})
public class MembershipHourlyProfileBucketResponse {

    @Schema(description = "Hour of the local day in the community's time zone, from 0 to 23",
            example = "13")
    private final int hour;
    @Schema(description = "Average energy consumed by every supply of the membership in this hour, " +
            "whatever its origin: grid import plus self-consumed energy. Averaged over the total " +
            "consumption samples found. Null when there is no sample, never 0.",
            example = "0.42", types = {"number", "null"})
    private final Double averageTotalConsumptionKWh;
    @Schema(description = "Number of hourly records carrying total consumption found for this hour across " +
            "every supply and every day of the month. A sample count, not a day count: on a daylight " +
            "saving day one local hour holds two samples and another none.",
            example = "62")
    private final long totalConsumptionSampleCount;
    @Schema(description = "Average energy assigned to every supply of the membership in this hour: " +
            "self-consumed plus surplus energy. Averaged over the assigned production samples found. " +
            "Null when there is no sample; 0 when there are samples and all of them are zero.",
            example = "1.18", types = {"number", "null"})
    private final Double averageAssignedProductionKWh;
    @Schema(description = "Number of hourly records carrying assigned production found for this hour " +
            "across every supply and every day of the month. A sample count, not a day count, and " +
            "independent of totalConsumptionSampleCount, since a record can carry consumption alone.",
            example = "62")
    private final long assignedProductionSampleCount;

    public MembershipHourlyProfileBucketResponse(HourlyProfileBucket bucket) {
        this.hour = bucket.getHour();
        this.averageTotalConsumptionKWh = bucket.getAverageConsumptionKWh();
        this.totalConsumptionSampleCount = bucket.getConsumptionSampleCount();
        this.averageAssignedProductionKWh = bucket.getAverageAssignedProductionKWh();
        this.assignedProductionSampleCount = bucket.getAssignedProductionSampleCount();
    }

    public int getHour() {
        return hour;
    }

    public Double getAverageTotalConsumptionKWh() {
        return averageTotalConsumptionKWh;
    }

    public long getTotalConsumptionSampleCount() {
        return totalConsumptionSampleCount;
    }

    public Double getAverageAssignedProductionKWh() {
        return averageAssignedProductionKWh;
    }

    public long getAssignedProductionSampleCount() {
        return assignedProductionSampleCount;
    }
}
