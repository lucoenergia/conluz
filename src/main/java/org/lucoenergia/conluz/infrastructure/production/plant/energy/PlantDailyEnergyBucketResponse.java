package org.lucoenergia.conluz.infrastructure.production.plant.energy;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergySeriesBucket;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * One local day of a plant's energy series: the production of the plant's own supply that day, with
 * the day's hours inside the period and those with a record.
 *
 * <p>The production is rounded once, to 3 decimals, on the unrounded sum.
 */
@Schema(requiredProperties = {"date", "time", "productionKWh", "hoursWithData", "expectedHours"})
public class PlantDailyEnergyBucketResponse {

    @Schema(description = "Local calendar date of the day, in the community's time zone.",
            example = "2024/01/15")
    private final String date;
    @Schema(description = "Always 00:00: the bucket covers the local day, as far as it lies inside the period.",
            example = "00:00")
    private final String time;
    @Schema(description = "Energy the plant produced over the day, as reported by the distributor for the " +
            "plant's own supply. In kWh, rounded to 3 decimals. Null when the plant stored no production " +
            "record for the day, which is not a measured zero.",
            example = "12.5", types = {"number", "null"})
    private final Double productionKWh;
    @Schema(description = "Number of hourly production records stored for the day inside the period, zeros " +
            "included. Below expectedHours, the day is incomplete.",
            example = "24")
    private final long hoursWithData;
    @Schema(description = "Number of the day's hours inside the period. Accounts for daylight saving transitions, so " +
            "a whole transition day spans 23 or 25 hours rather than 24.",
            example = "24")
    private final long expectedHours;

    public PlantDailyEnergyBucketResponse(PlantEnergySeriesBucket bucket) {
        OffsetDateTime startsAt = bucket.getStartsAt();
        this.date = startsAt.format(DateTimeFormatter.ofPattern(DateConverter.DATE_FORMAT));
        this.time = startsAt.format(DateTimeFormatter.ofPattern(DateConverter.TIME_FORMAT));
        this.productionKWh = PlantEnergyMetricsEnergyResponse.round(bucket.getProductionKWh());
        this.hoursWithData = bucket.getHoursWithData();
        this.expectedHours = bucket.getExpectedHours();
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
