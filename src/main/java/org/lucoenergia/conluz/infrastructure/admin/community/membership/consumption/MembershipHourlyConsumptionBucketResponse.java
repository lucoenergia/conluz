package org.lucoenergia.conluz.infrastructure.admin.community.membership.consumption;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.membership.consumption.MembershipHourlyConsumptionBucket;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * One hour of a membership's consumption series: the hourly figures of every supply the member owns
 * in the community that stored a record that hour, added up, with the number of those supplies and of
 * those that stored a record that hour.
 *
 * <p>The same fields as {@link MembershipDailyConsumptionBucketResponse}, rounded by the same
 * {@link MembershipConsumptionBucketFigures}, plus the instant the hour starts at. That instant is
 * what identifies an hour: on the day the clocks go back, two consecutive hours share their
 * {@code date} and {@code time}.
 */
@Schema(requiredProperties = {"startsAt", "date", "time", "gridImportKWh", "surplusKWh", "selfConsumptionKWh",
        "assignedProductionKWh", "savingsEur", "tariffSource", "supplyCount", "suppliesWithData"})
public class MembershipHourlyConsumptionBucketResponse {

    @Schema(description = "Instant the hour starts at, with the offset in force in the community's time " +
            "zone at that instant. Unique within the series, including on the day the clocks go back.",
            example = "2026-10-25T02:00:00+01:00", format = "date-time")
    private final OffsetDateTime startsAt;
    @Schema(description = "Local calendar date the hour starts on, in the community's time zone.",
            example = "2024/01/15")
    private final String date;
    @Schema(description = "Local time the hour starts at, in the community's time zone, from 00:00 to " +
            "23:00. On the day the clocks go back, two consecutive hours carry the same date and time.",
            example = "13:00")
    private final String time;
    @Schema(description = "Energy taken from the grid by every supply of the membership over the " +
            "hour. Does not include self-consumed energy. In kWh, rounded to 3 decimals. 0 when no " +
            "supply stored a record.",
            example = "0.75")
    private final BigDecimal gridImportKWh;
    @Schema(description = "Energy assigned to every supply of the membership and fed back to the " +
            "grid over the hour. In kWh, rounded to 3 decimals.",
            example = "0.25")
    private final BigDecimal surplusKWh;
    @Schema(description = "Energy produced by the community and consumed by every supply of the " +
            "membership over the hour, as reported by the distributor. In kWh, rounded to 3 " +
            "decimals.",
            example = "0.5")
    private final BigDecimal selfConsumptionKWh;
    @Schema(description = "Energy assigned to every supply of the membership over the hour: " +
            "selfConsumptionKWh plus surplusKWh. In kWh, rounded to 3 decimals after summing, so it " +
            "can differ by 0.001 from the sum of the rounded figures.",
            example = "0.75")
    private final BigDecimal assignedProductionKWh;
    @Schema(description = "Estimated amount the hour's self-consumed energy saved across every " +
            "supply that stored a record, in euros, rounded to cents. Null when no supply stored a " +
            "record that hour: an hour with nothing stored is not an hour that saved nothing. Unlike the " +
            "per-supply series, which never reports null here. A supply that did store a record with " +
            "no self-consumption is a measured zero and makes the amount 0.00, not null. Null " +
            "exactly when tariffSource is null.",
            example = "0.08", types = {"number", "null"})
    private final BigDecimal savingsEur;
    @Schema(description = "Whether the prices behind savingsEur are the supplies' contracted tariffs " +
            "or an estimate. A single estimated stretch of any supply makes the whole amount an " +
            "estimate. Null exactly when savingsEur is null: with no amount there is nothing whose " +
            "prices to describe.",
            example = "ESTIMATE", types = {"string", "null"})
    private final TariffSource tariffSource;
    @Schema(description = "Number of supplies the member currently owns in the community. The model " +
            "records no date a supply joined, so the same count applies to every hour, including " +
            "hours before a supply started reporting.",
            example = "3")
    private final int supplyCount;
    @Schema(description = "Number of those supplies that stored a record that hour. Measured hour by " +
            "hour, whereas the aggregated energy metrics measure their suppliesWithData over the whole " +
            "period, so the two figures are not comparable across endpoints. Below supplyCount, the " +
            "hour is incomplete.",
            example = "2")
    private final int suppliesWithData;

    public MembershipHourlyConsumptionBucketResponse(MembershipHourlyConsumptionBucket bucket) {
        MembershipConsumptionBucketFigures figures = new MembershipConsumptionBucketFigures(bucket.getTotals());
        this.startsAt = bucket.getStartsAt();
        this.date = startsAt.format(DateTimeFormatter.ofPattern(DateConverter.DATE_FORMAT));
        this.time = startsAt.format(DateTimeFormatter.ofPattern(DateConverter.TIME_FORMAT));
        this.gridImportKWh = figures.gridImportKWh();
        this.surplusKWh = figures.surplusKWh();
        this.selfConsumptionKWh = figures.selfConsumptionKWh();
        this.assignedProductionKWh = figures.assignedProductionKWh();
        this.savingsEur = figures.savingsEur();
        this.tariffSource = figures.tariffSource();
        this.supplyCount = figures.supplyCount();
        this.suppliesWithData = figures.suppliesWithData();
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
