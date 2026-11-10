package org.lucoenergia.conluz.infrastructure.production.plant.energy;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergySeriesBucket;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * One local month of a plant's energy series: the production of the plant's own supply that month,
 * totalled from its hourly records, with the month's hours inside the period and those with a record.
 *
 * <p>The production is rounded once, to 3 decimals, on the unrounded sum.
 */
@Schema(requiredProperties = {"date", "time", "productionKWh", "hoursWithData", "expectedHours"})
public class PlantMonthlyEnergyBucketResponse {

    @Schema(description = "First day of the month, a local calendar date in the community's time zone.",
            example = "2024/01/01")
    private final String date;
    @Schema(description = "Always 00:00: the bucket covers the local month, as far as it lies inside the period.",
            example = "00:00")
    private final String time;
    @Schema(description = "Energy the plant produced over the month, as reported by the distributor for the " +
            "plant's own supply. In kWh, rounded to 3 decimals. Null when the plant stored no production " +
            "record for the month, which is not a measured zero.",
            example = "12.5", types = {"number", "null"})
    private final Double productionKWh;
    @Schema(description = "Number of hourly production records stored for the month inside the period, zeros " +
            "included. Below expectedHours, the month is incomplete.",
            example = "480")
    private final long hoursWithData;
    @Schema(description = "Number of the month's hours inside the period. Accounts for daylight saving transitions, " +
            "so a whole transition month spans one hour less or more.",
            example = "744")
    private final long expectedHours;

    public PlantMonthlyEnergyBucketResponse(PlantEnergySeriesBucket bucket) {
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
