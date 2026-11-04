package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;
import org.lucoenergia.conluz.domain.consumption.SupplyHourlyConsumptionBucket;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One hour of a supply's hourly consumption series.
 *
 * <p>It carries the fields of {@link SupplyConsumptionBucketResponse}, with the same names and
 * meaning, mapped by the same {@link SupplyConsumptionBucketValues}, plus the instant the hour starts
 * at. That instant is what identifies an hour: on the day the clocks go back, two consecutive hours
 * share their {@code date} and {@code time}. It is a type of its own so that the daily and monthly
 * buckets do not gain a field that only means something for an hour.
 */
@Schema(requiredProperties = {"cups", "startsAt", "date", "time", "gridImportKWh", "obtainMethod",
        "surplusKWh", "selfConsumptionKWh", "assignedProductionKWh", "savingsEur", "tariffSource"})
public class SupplyHourlyConsumptionBucketResponse {

    @Schema(description = "CUPS code of the supply the hour belongs to.",
            example = "ES0031406912345678JN0F")
    private final String cups;
    @Schema(description = "Instant the hour starts at, with the offset in force in the supply's time " +
            "zone at that instant. Unique within the series, including on the day the clocks go back.",
            example = "2026-10-25T02:00:00+01:00", format = "date-time")
    private final OffsetDateTime startsAt;
    @Schema(description = "Local calendar date the hour starts on, in the zone the application is " +
            "configured with.",
            example = "2023/04/10")
    private final String date;
    @Schema(description = "Local time the hour starts at, in the zone the application is configured " +
            "with. Two consecutive hours share it on the day the clocks go back.",
            example = "13:00")
    private final String time;
    @Schema(description = "Energy taken from the grid by this supply over the hour. Does not " +
            "include self-consumed energy. In kWh, rounded to 3 decimals.",
            example = "0.5")
    private final Float gridImportKWh;
    @Schema(description = "How the underlying record was obtained, as reported by Datadis. Null " +
            "on an hour with no stored record.",
            example = "Real", types = {"string", "null"})
    private final String obtainMethod;
    @Schema(description = "Energy assigned to this supply and fed back to the grid over the hour. " +
            "In kWh, rounded to 3 decimals.",
            example = "0.25")
    private final Float surplusKWh;
    @Schema(description = "Energy produced by the community and consumed by this supply over the " +
            "hour, as reported by the distributor. In kWh, rounded to 3 decimals.",
            example = "0.75")
    private final Float selfConsumptionKWh;
    @Schema(description = "Energy assigned to this supply over the hour: selfConsumptionKWh plus " +
            "surplusKWh. In kWh, rounded to 3 decimals after summing, so it can differ by 0.001 from " +
            "the sum of the rounded figures.",
            example = "1.0")
    private final Float assignedProductionKWh;
    @Schema(description = "Estimated amount the hour's self-consumed energy saved, in euros, " +
            "rounded to cents. An hour with no self-consumption -- and an hour with no stored " +
            "record at all -- reports 0.00.",
            example = "0.11")
    private final BigDecimal savingsEur;
    @Schema(description = "Whether the prices behind the amount are the supply's contracted " +
            "tariff or an estimate.",
            example = "ESTIMATE")
    private final TariffSource tariffSource;

    public SupplyHourlyConsumptionBucketResponse(SupplyHourlyConsumptionBucket hour) {
        SupplyConsumptionBucketValues values = SupplyConsumptionBucketValues.of(hour.getBucket());
        this.cups = values.cups();
        this.startsAt = hour.getStartsAt();
        this.date = values.date();
        this.time = values.time();
        this.gridImportKWh = values.gridImportKWh();
        this.obtainMethod = values.obtainMethod();
        this.surplusKWh = values.surplusKWh();
        this.selfConsumptionKWh = values.selfConsumptionKWh();
        this.assignedProductionKWh = values.assignedProductionKWh();
        this.savingsEur = values.savingsEur();
        this.tariffSource = values.tariffSource();
    }

    public String getCups() {
        return cups;
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

    public Float getGridImportKWh() {
        return gridImportKWh;
    }

    public String getObtainMethod() {
        return obtainMethod;
    }

    public Float getSurplusKWh() {
        return surplusKWh;
    }

    public Float getSelfConsumptionKWh() {
        return selfConsumptionKWh;
    }

    public Float getAssignedProductionKWh() {
        return assignedProductionKWh;
    }

    public BigDecimal getSavingsEur() {
        return savingsEur;
    }

    public TariffSource getTariffSource() {
        return tariffSource;
    }
}
