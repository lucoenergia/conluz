package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergySeriesBucket;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * One hour of a community's energy series: the self-consumption and surplus of every supply of the
 * community with data that hour, added up, with the number of supplies in scope and of those with
 * data. The instant the hour starts at identifies it: on the day the clocks go back, two consecutive
 * hours share their {@code date} and {@code time}.
 *
 * <p>Each energy figure is rounded once, to 3 decimals, on the unrounded sum.
 */
@Schema(requiredProperties = {"startsAt", "date", "time", "selfConsumptionKWh",
        "surplusKWh", "supplyCount", "suppliesWithData"})
public class CommunityHourlyEnergyBucketResponse {

    @Schema(description = "Instant the hour starts at, with the offset in force in the community's time " +
            "zone at that instant. Unique within the series, including on the day the clocks go back.",
            example = "2026-10-25T02:00:00+01:00", format = "date-time")
    private final OffsetDateTime startsAt;
    @Schema(description = "Local calendar date the hour starts on, in the community's time zone.",
            example = "2024/01/15")
    private final String date;
    @Schema(description = "Local time the hour starts at, in the community's time zone, from 00:00 to 23:00. On the " +
            "day the clocks go back, two consecutive hours carry the same date and time.",
            example = "13:00")
    private final String time;
    @Schema(description = "Energy produced by the community and consumed by every supply of the community with data " +
            "over the hour, as reported by the distributor. In kWh, rounded to 3 decimals. Null when " +
            "no supply had data that hour, which is not a measured zero.",
            example = "12.5", types = {"number", "null"})
    private final Double selfConsumptionKWh;
    @Schema(description = "Energy assigned to every supply of the community with data and fed back to the grid over " +
            "the hour. In kWh, rounded to 3 decimals. Null when no supply had data that hour, which " +
            "is not a measured zero.",
            example = "3.25", types = {"number", "null"})
    private final Double surplusKWh;
    @Schema(description = "Number of enabled supplies of the community, whatever the membership status of their " +
            "owners. The model records no date a supply joined, so the same count applies to every " +
            "hour.",
            example = "12")
    private final int supplyCount;
    @Schema(description = "Number of those supplies with data that hour: whose record of that hour carries " +
            "self-consumed energy, zero included. Below supplyCount, the hour is incomplete.",
            example = "11")
    private final int suppliesWithData;

    public CommunityHourlyEnergyBucketResponse(CommunityEnergySeriesBucket bucket) {
        OffsetDateTime startsAt = bucket.getStartsAt();
        this.startsAt = startsAt;
        this.date = startsAt.format(DateTimeFormatter.ofPattern(DateConverter.DATE_FORMAT));
        this.time = startsAt.format(DateTimeFormatter.ofPattern(DateConverter.TIME_FORMAT));
        this.selfConsumptionKWh = CommunityEnergyMetricsEnergyResponse.round(bucket.getSelfConsumptionKWh());
        this.surplusKWh = CommunityEnergyMetricsEnergyResponse.round(bucket.getSurplusKWh());
        this.supplyCount = bucket.getSupplyCount();
        this.suppliesWithData = bucket.getSuppliesWithData();
    }

    public OffsetDateTime getStartsAt() {
        return startsAt;
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
