package org.lucoenergia.conluz.infrastructure.admin.community.membership.consumption;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.membership.consumption.MembershipDailyConsumptionBucket;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * One local calendar day of a membership's consumption series: the daily figures of every supply
 * the member owns in the community that stored a record that day, added up, with the number of
 * those supplies and of those that stored a record that day.
 *
 * <p>The same fields as {@link MembershipMonthlyConsumptionBucketResponse}, rounded by the same
 * {@link MembershipConsumptionBucketFigures}; a schema of its own so that each name keeps the
 * length of its bucket.
 */
@Schema(requiredProperties = {"date", "time", "gridImportKWh", "surplusKWh", "selfConsumptionKWh",
        "assignedProductionKWh", "savingsEur", "tariffSource", "supplyCount", "suppliesWithData"})
public class MembershipDailyConsumptionBucketResponse {

    @Schema(description = "The day, a local calendar date in the community's time zone.",
            example = "2024/01/15")
    private final String date;
    @Schema(description = "Local time the day starts at in the community's time zone: always " +
            "midnight, also for a first day the period starts in the middle of.",
            example = "00:00")
    private final String time;
    @Schema(description = "Energy taken from the grid by every supply of the membership over the " +
            "day. Does not include self-consumed energy. In kWh, rounded to 3 decimals. 0 when no " +
            "supply stored a record.",
            example = "7.75")
    private final BigDecimal gridImportKWh;
    @Schema(description = "Energy assigned to every supply of the membership and fed back to the " +
            "grid over the day. In kWh, rounded to 3 decimals.",
            example = "1.75")
    private final BigDecimal surplusKWh;
    @Schema(description = "Energy produced by the community and consumed by every supply of the " +
            "membership over the day, as reported by the distributor. In kWh, rounded to 3 " +
            "decimals.",
            example = "2.5")
    private final BigDecimal selfConsumptionKWh;
    @Schema(description = "Energy assigned to every supply of the membership over the day: " +
            "selfConsumptionKWh plus surplusKWh. In kWh, rounded to 3 decimals after summing, so it " +
            "can differ by 0.001 from the sum of the rounded figures.",
            example = "4.25")
    private final BigDecimal assignedProductionKWh;
    @Schema(description = "Estimated amount the day's self-consumed energy saved across every " +
            "supply that stored a record, in euros, rounded to cents. Null when no supply stored a " +
            "record that day: a day with nothing stored is not a day that saved nothing. Unlike the " +
            "per-supply series, which never reports null here. A supply that did store a record with " +
            "no self-consumption is a measured zero and makes the amount 0.00, not null. Null " +
            "exactly when tariffSource is null.",
            example = "0.38", types = {"number", "null"})
    private final BigDecimal savingsEur;
    @Schema(description = "Whether the prices behind savingsEur are the supplies' contracted tariffs " +
            "or an estimate. A single estimated stretch of any supply makes the whole amount an " +
            "estimate. Null exactly when savingsEur is null: with no amount there is nothing whose " +
            "prices to describe.",
            example = "ESTIMATE", types = {"string", "null"})
    private final TariffSource tariffSource;
    @Schema(description = "Number of supplies the member currently owns in the community. The model " +
            "records no date a supply joined, so the same count applies to every day, including " +
            "days before a supply started reporting.",
            example = "3")
    private final int supplyCount;
    @Schema(description = "Number of those supplies that stored at least one hourly record that day, " +
            "within the period. Measured day by day, whereas the aggregated energy metrics measure " +
            "their suppliesWithData over the whole period, so the two figures are not comparable " +
            "across endpoints. Below supplyCount, the day is incomplete.",
            example = "2")
    private final int suppliesWithData;

    public MembershipDailyConsumptionBucketResponse(MembershipDailyConsumptionBucket bucket) {
        MembershipConsumptionBucketFigures figures = new MembershipConsumptionBucketFigures(bucket.getTotals());
        this.date = bucket.getDay().format(DateTimeFormatter.ofPattern(DateConverter.DATE_FORMAT));
        this.time = LocalTime.MIDNIGHT.format(DateTimeFormatter.ofPattern(DateConverter.TIME_FORMAT));
        this.gridImportKWh = figures.gridImportKWh();
        this.surplusKWh = figures.surplusKWh();
        this.selfConsumptionKWh = figures.selfConsumptionKWh();
        this.assignedProductionKWh = figures.assignedProductionKWh();
        this.savingsEur = figures.savingsEur();
        this.tariffSource = figures.tariffSource();
        this.supplyCount = figures.supplyCount();
        this.suppliesWithData = figures.suppliesWithData();
    }

    public String getDate() {
        return date;
    }

    public String getTime() {
        return time;
    }

    public BigDecimal getGridImportKWh() {
        return gridImportKWh;
    }

    public BigDecimal getSurplusKWh() {
        return surplusKWh;
    }

    public BigDecimal getSelfConsumptionKWh() {
        return selfConsumptionKWh;
    }

    public BigDecimal getAssignedProductionKWh() {
        return assignedProductionKWh;
    }

    public BigDecimal getSavingsEur() {
        return savingsEur;
    }

    public TariffSource getTariffSource() {
        return tariffSource;
    }

    public int getSupplyCount() {
        return supplyCount;
    }

    public int getSuppliesWithData() {
        return suppliesWithData;
    }
}
