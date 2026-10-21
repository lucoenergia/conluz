package org.lucoenergia.conluz.infrastructure.admin.community.membership.consumption;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.membership.consumption.MembershipMonthlyConsumptionBucket;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;
import org.lucoenergia.conluz.domain.consumption.SupplySavings;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.lucoenergia.conluz.infrastructure.shared.web.KWhRounding;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * One local calendar month of a membership's consumption series: the monthly figures of every supply
 * the member owns in the community, added up, with the number of those supplies and of those that
 * stored a record that month.
 *
 * <p>Derived from the per-supply bucket but not identical: {@code cups} and {@code obtainMethod}
 * describe one supply and have no single subject once several are folded, so they are dropped; the
 * two counters carry the month's completeness instead.
 *
 * <p>The energy figures and the savings are rounded here, once, for presentation, as in the
 * per-supply bucket: each figure is the unrounded sum of the supplies' figures, rounded as a whole.
 */
@Schema(requiredProperties = {"date", "time", "consumptionKWh", "surplusEnergyKWh", "generationEnergyKWh",
        "selfConsumptionEnergyKWh", "savingsEur", "tariffSource", "supplyCount", "suppliesWithData"})
public class MembershipMonthlyConsumptionBucketResponse {

    @Schema(description = "First day of the month, a local calendar date in the community's " +
            "time zone.",
            example = "2024/01/01")
    private final String date;
    @Schema(description = "Local time the month starts at in the community's time zone: always " +
            "midnight.",
            example = "00:00")
    private final String time;
    @Schema(description = "Energy taken from the grid by every supply of the membership over the " +
            "month. Does not include self-consumed energy. In kWh, rounded to 3 decimals. 0 when no " +
            "supply stored a record.",
            example = "107.75")
    private final BigDecimal consumptionKWh;
    @Schema(description = "Energy assigned to every supply of the membership and fed back to the " +
            "grid over the month. In kWh, rounded to 3 decimals.",
            example = "21.75")
    private final BigDecimal surplusEnergyKWh;
    @Schema(description = "Energy generated and assigned to every supply of the membership over the " +
            "month, in kWh, rounded to 3 decimals.",
            example = "63")
    private final BigDecimal generationEnergyKWh;
    @Schema(description = "Energy produced by the community and consumed by every supply of the " +
            "membership over the month, as reported by the distributor. In kWh, rounded to 3 " +
            "decimals.",
            example = "42.5")
    private final BigDecimal selfConsumptionEnergyKWh;
    @Schema(description = "Estimated amount the month's self-consumed energy saved across every " +
            "supply that stored a record, in euros, rounded to cents. Null when no supply stored a " +
            "record that month: a month with nothing stored is not a month that saved nothing. Unlike " +
            "the per-supply series, which never reports null here. A supply that did store a record " +
            "with no self-consumption is a measured zero and makes the amount 0.00, not null. Null " +
            "exactly when tariffSource is null.",
            example = "6.38", types = {"number", "null"})
    private final BigDecimal savingsEur;
    @Schema(description = "Whether the prices behind savingsEur are the supplies' contracted tariffs " +
            "or an estimate. A single estimated stretch of any supply makes the whole amount an " +
            "estimate. Null exactly when savingsEur is null: with no amount there is nothing whose " +
            "prices to describe.",
            example = "ESTIMATE", types = {"string", "null"})
    private final TariffSource tariffSource;
    @Schema(description = "Number of supplies the member currently owns in the community. The model " +
            "records no date a supply joined, so the same count applies to every month, including " +
            "months before a supply started reporting.",
            example = "3")
    private final int supplyCount;
    @Schema(description = "Number of those supplies that stored a monthly record for this month. " +
            "Measured from stored monthly records, month by month, whereas the aggregated energy " +
            "metrics measure their suppliesWithData from hourly records over the whole period: same " +
            "name and intent, different unit, so the two figures are not comparable across endpoints. " +
            "Below supplyCount, the month is incomplete.",
            example = "2")
    private final int suppliesWithData;

    public MembershipMonthlyConsumptionBucketResponse(MembershipMonthlyConsumptionBucket bucket) {
        this.date = bucket.getMonth().atDay(1).format(DateTimeFormatter.ofPattern(DateConverter.DATE_FORMAT));
        this.time = LocalTime.MIDNIGHT.format(DateTimeFormatter.ofPattern(DateConverter.TIME_FORMAT));
        this.consumptionKWh = rounded(bucket.getConsumptionKWh());
        this.surplusEnergyKWh = rounded(bucket.getSurplusEnergyKWh());
        this.generationEnergyKWh = rounded(bucket.getGenerationEnergyKWh());
        this.selfConsumptionEnergyKWh = rounded(bucket.getSelfConsumptionEnergyKWh());
        Optional<SupplySavings> savings = bucket.getSavings();
        // Scale is declared inline rather than as a constant: ResponseSchemaNullabilityArchTest
        // inspects every field of a *Response class, static ones included.
        int cents = 2;
        this.savingsEur = savings.map(s -> s.getAmountEur().setScale(cents, RoundingMode.HALF_UP)).orElse(null);
        this.tariffSource = savings.map(SupplySavings::getTariffSource).orElse(null);
        this.supplyCount = bucket.getSupplyCount();
        this.suppliesWithData = bucket.getSuppliesWithData();
    }

    private static BigDecimal rounded(BigDecimal kWh) {
        return BigDecimal.valueOf(KWhRounding.round(kWh.doubleValue()));
    }

    public String getDate() {
        return date;
    }

    public String getTime() {
        return time;
    }

    public BigDecimal getConsumptionKWh() {
        return consumptionKWh;
    }

    public BigDecimal getSurplusEnergyKWh() {
        return surplusEnergyKWh;
    }

    public BigDecimal getGenerationEnergyKWh() {
        return generationEnergyKWh;
    }

    public BigDecimal getSelfConsumptionEnergyKWh() {
        return selfConsumptionEnergyKWh;
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
