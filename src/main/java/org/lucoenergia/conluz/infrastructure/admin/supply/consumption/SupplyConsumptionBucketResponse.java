package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;
import org.lucoenergia.conluz.domain.consumption.SupplyConsumptionBucket;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;

import java.math.BigDecimal;

/**
 * One bucket of a supply's consumption series -- a local calendar day on the daily endpoint, a local
 * calendar month on the monthly one, an hour on the hourly one.
 *
 * <p>The granularities share this shape because they carry the same fields with the same meaning;
 * only the width of the bucket differs, and the caller already chose it through the URL. An hour
 * also needs the instant it starts at, which {@link SupplyHourlyConsumptionBucketResponse} adds.
 *
 * <p>This is the response contract, deliberately separate from {@link DatadisConsumption}, which is
 * the shape the external Datadis API speaks and is still serialised directly by the yearly
 * endpoint. Detaching them drops {@code empty} -- a boolean that never existed as data and
 * only appeared in the JSON because {@code isEmpty()} is a getter -- and makes room for the savings
 * Datadis knows nothing about.
 *
 * <p>The energy figures carry the names the energy metrics give the same quantities (#460).
 * assignedProductionKWh is derived from the unrounded self-consumed and surplus energy; the
 * distributor's generation figure is not exposed.
 *
 * <p>The energy figures and the amount are rounded by {@link SupplyConsumptionBucketValues}, the
 * mapping every series response shares.
 */
@Schema(requiredProperties = {"cups", "date", "time", "gridImportKWh", "obtainMethod",
        "surplusKWh", "selfConsumptionKWh", "assignedProductionKWh", "savingsEur", "tariffSource"})
public class SupplyConsumptionBucketResponse {

    @Schema(description = "CUPS code of the supply the bucket belongs to.",
            example = "ES0031406912345678JN0F")
    private final String cups;
    @Schema(description = "Local calendar date the bucket starts on: the day itself on the daily " +
            "series, the first day of the month on the monthly one, the hour's day on the hourly one.",
            example = "2023/04/10")
    private final String date;
    @Schema(description = "Local time the bucket starts at, in the zone the application is " +
            "configured with. On the hourly series, two consecutive hours share it on the day the " +
            "clocks go back.",
            example = "00:00")
    private final String time;
    @Schema(description = "Energy taken from the grid by this supply over the bucket. Does not " +
            "include self-consumed energy. In kWh, rounded to 3 decimals.",
            example = "12.5")
    private final Float gridImportKWh;
    @Schema(description = "How the underlying records were obtained, as reported by Datadis. Null " +
            "on a bucket with no stored record.",
            example = "Real", types = {"string", "null"})
    private final String obtainMethod;
    @Schema(description = "Energy assigned to this supply and fed back to the grid over the bucket. " +
            "In kWh, rounded to 3 decimals.",
            example = "3.25")
    private final Float surplusKWh;
    @Schema(description = "Energy produced by the community and consumed by this supply over the " +
            "bucket, as reported by the distributor. In kWh, rounded to 3 decimals.",
            example = "5.5")
    private final Float selfConsumptionKWh;
    @Schema(description = "Energy assigned to this supply over the bucket: selfConsumptionKWh plus " +
            "surplusKWh. In kWh, rounded to 3 decimals after summing, so it can differ by 0.001 from " +
            "the sum of the rounded figures.",
            example = "8.75")
    private final Float assignedProductionKWh;
    @Schema(description = "Estimated amount the bucket's self-consumed energy saved, in euros, " +
            "rounded to cents. A bucket with no self-consumption -- and a bucket with no stored " +
            "record at all -- reports 0.00.",
            example = "0.83")
    private final BigDecimal savingsEur;
    @Schema(description = "Whether the prices behind the amount are the supply's contracted " +
            "tariff or an estimate. A single estimated stretch of the bucket makes the whole " +
            "amount an estimate.",
            example = "ESTIMATE")
    private final TariffSource tariffSource;

    public SupplyConsumptionBucketResponse(SupplyConsumptionBucket bucket) {
        SupplyConsumptionBucketValues values = SupplyConsumptionBucketValues.of(bucket);
        this.cups = values.cups();
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
