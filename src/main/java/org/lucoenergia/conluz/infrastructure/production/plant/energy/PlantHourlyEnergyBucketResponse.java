package org.lucoenergia.conluz.infrastructure.production.plant.energy;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergySeriesBucket;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * One hour of a plant's energy series: the production of the plant's own supply that hour, or a gap.
 * The instant the hour starts at identifies it: on the day the clocks go back, two consecutive hours
 * share their {@code date} and {@code time}.
 *
 * <p>The production is rounded once, to 3 decimals, on the unrounded sum.
 */
@Schema(requiredProperties = {"startsAt", "date", "time", "productionKWh", "hoursWithData", "expectedHours"})
public class PlantHourlyEnergyBucketResponse {

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
    @Schema(description = "Energy the plant exported to the grid at its connection point over the hour, as " +
            "measured by the distributor for the plant's own supply (net of the plant's own consumption). In " +
            "kWh, rounded to 3 decimals. Null when the plant stored no production record for the hour, which " +
            "is not a measured zero.",
            example = "12.5", types = {"number", "null"})
    private final Double productionKWh;
    @Schema(description = "Number of hourly production records stored for the hour: 1, or 0 when the hour is a gap.",
            example = "1")
    private final long hoursWithData;
    @Schema(description = "Number of hours the bucket spans inside the period: always 1.",
            example = "1")
    private final long expectedHours;

    public PlantHourlyEnergyBucketResponse(PlantEnergySeriesBucket bucket) {
        OffsetDateTime startsAt = bucket.getStartsAt();
        this.startsAt = startsAt;
        this.date = startsAt.format(DateTimeFormatter.ofPattern(DateConverter.DATE_FORMAT));
        this.time = startsAt.format(DateTimeFormatter.ofPattern(DateConverter.TIME_FORMAT));
        this.productionKWh = PlantEnergyMetricsEnergyResponse.round(bucket.getProductionKWh());
        this.hoursWithData = bucket.getHoursWithData();
        this.expectedHours = bucket.getExpectedHours();
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

    public Double getProductionKWh() {
        return productionKWh;
    }

    public long getHoursWithData() {
        return hoursWithData;
    }

    public long getExpectedHours() {
        return expectedHours;
    }
}
