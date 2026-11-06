package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergySeriesBucket;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * One local day of a community's energy series: the self-consumption and surplus of every supply of
 * the community with data that day, added up, with the number of supplies in scope and of those with
 * data.
 *
 * <p>Each energy figure is rounded once, to 3 decimals, on the unrounded sum.
 */
@Schema(requiredProperties = {"date", "time", "selfConsumptionKWh", "surplusKWh",
        "supplyCount", "suppliesWithData"})
public class CommunityDailyEnergyBucketResponse {

    @Schema(description = "The day, a local calendar date in the community's time zone.",
            example = "2024/01/15")
    private final String date;
    @Schema(description = "Always 00:00: the bucket covers the whole local day.",
            example = "00:00")
    private final String time;
    @Schema(description = "Energy produced by the community and consumed by every supply of the community with data " +
            "over the day, as reported by the distributor. In kWh, rounded to 3 decimals. Null when " +
            "no supply had data that day, which is not a measured zero.",
            example = "12.5", types = {"number", "null"})
    private final Double selfConsumptionKWh;
    @Schema(description = "Energy assigned to every supply of the community with data and fed back to the grid over " +
            "the day. In kWh, rounded to 3 decimals. Null when no supply had data that day, which is " +
            "not a measured zero.",
            example = "3.25", types = {"number", "null"})
    private final Double surplusKWh;
    @Schema(description = "Number of enabled supplies of the community, whatever the membership status of their " +
            "owners. The model records no date a supply joined, so the same count applies to every " +
            "day.",
            example = "12")
    private final int supplyCount;
    @Schema(description = "Number of those supplies with data that day: whose hourly records carrying self-consumed " +
            "energy, zero included, cover at least 90% of the day's hours inside the period. Below " +
            "supplyCount, the day is incomplete.",
            example = "11")
    private final int suppliesWithData;

    public CommunityDailyEnergyBucketResponse(CommunityEnergySeriesBucket bucket) {
        OffsetDateTime startsAt = bucket.getStartsAt();
        this.date = startsAt.format(DateTimeFormatter.ofPattern(DateConverter.DATE_FORMAT));
        this.time = startsAt.format(DateTimeFormatter.ofPattern(DateConverter.TIME_FORMAT));
        this.selfConsumptionKWh = CommunityEnergyMetricsEnergyResponse.round(bucket.getSelfConsumptionKWh());
        this.surplusKWh = CommunityEnergyMetricsEnergyResponse.round(bucket.getSurplusKWh());
        this.supplyCount = bucket.getSupplyCount();
        this.suppliesWithData = bucket.getSuppliesWithData();
    }

    public String getDate() {
        return date;
    }

    public String getTime() {
        return time;
    }

    public Double getSelfConsumptionKWh() {
        return selfConsumptionKWh;
    }

    public Double getSurplusKWh() {
        return surplusKWh;
    }

    public int getSupplyCount() {
        return supplyCount;
    }

    public int getSuppliesWithData() {
        return suppliesWithData;
    }
}
