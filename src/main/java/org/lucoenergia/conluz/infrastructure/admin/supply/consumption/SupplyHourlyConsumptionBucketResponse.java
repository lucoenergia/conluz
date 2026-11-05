package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.consumption.SupplyHourlyConsumptionBucket;

import java.time.OffsetDateTime;

/**
 * One hour of a supply's hourly consumption series.
 *
 * <p>It carries the fields of {@link SupplyConsumptionBucketResponse}, with the same names and
 * meaning, plus the instant the hour starts at. That instant is what identifies an hour: on the day
 * the clocks go back, two consecutive hours share their {@code date} and {@code time}. It is a type
 * of its own so that the daily and monthly buckets do not gain a field that only means something for
 * an hour.
 */
@Schema(requiredProperties = {"cups", "startsAt", "date", "time", "gridImportKWh", "obtainMethod",
        "surplusKWh", "selfConsumptionKWh", "assignedProductionKWh", "savingsEur", "tariffSource"})
public class SupplyHourlyConsumptionBucketResponse extends SupplyConsumptionBucketResponse {

    @Schema(description = "Instant the hour starts at, with the offset in force in the supply's time " +
            "zone at that instant. Unique within the series, including on the day the clocks go back.",
            example = "2026-10-25T02:00:00+01:00", format = "date-time")
    private final OffsetDateTime startsAt;

    public SupplyHourlyConsumptionBucketResponse(SupplyHourlyConsumptionBucket hour) {
        super(hour.getBucket());
        this.startsAt = hour.getStartsAt();
    }

    public OffsetDateTime getStartsAt() {
        return startsAt;
    }
}
