package org.lucoenergia.conluz.infrastructure.admin.community.membership.payback;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.membership.payback.MembershipPayback;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;
import org.lucoenergia.conluz.infrastructure.admin.supply.consumption.EstimatedPriceResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Every field's JSON key is always present, so all eight are required; the values of all but
 * {@code tariffSource} may be null, which is what the {@code types} declarations say. A client
 * must be able to tell "no investment recorded" from "an investment of zero", and collapsing the
 * two would make the payback card show a recovered investment where none exists.
 *
 * <p>This is the only place amounts are rounded. The domain keeps them unrounded so a figure summed
 * over several supplies and several tariff segments is rounded once, here, rather than once per
 * part.
 */
@Schema(requiredProperties = {"investmentEur", "savedEur", "remainingEur", "progressRatio",
        "startDate", "estimatedRemainingMonths", "tariffSource", "estimatedPrice"})
public class MembershipPaybackResponse {

    @Schema(description = "What the member initially contributed, in euros. Null when no investment has been recorded for this membership, which is not the same as an investment of zero.",
            example = "1500.00", types = {"number", "null"})
    private final BigDecimal investmentEur;

    @Schema(description = "Estimated value of the self-consumed energy of all the member's supplies in this community since startDate, in euros. It prices the energy term before taxes only; VAT is applied only where the resolved tariff carries a rate. Null when the community has never activated a partition coefficient, so there is no period to price; zero when the period exists but the member's supplies consumed nothing from it.",
            example = "372.75", types = {"number", "null"})
    private final BigDecimal savedEur;

    @Schema(description = "What is left to recover, in euros, floored at zero once the investment has been recovered. Null when either amount above is null.",
            example = "1127.25", types = {"number", "null"})
    private final BigDecimal remainingEur;

    @Schema(description = "Savings divided by investment. Not capped: a member who has recovered more than they contributed reports a value above 1. Null when either amount is null.",
            example = "0.2485", types = {"number", "null"})
    private final BigDecimal progressRatio;

    @Schema(description = "The civil date, in the community's time zone, when the community first activated a partition coefficient and therefore began sharing energy. Null when it never has. Community-wide, not per member.",
            example = "2025-01-01", format = "date", types = {"string", "null"})
    private final LocalDate startDate;

    @Schema(description = "Whole months to recover the rest at the average daily rate observed since startDate, rounded up. Zero when the investment has already been recovered. Null when no rate can be established: no investment, no savings yet, or today being startDate itself.",
            example = "34", types = {"integer", "null"})
    private final Integer estimatedRemainingMonths;

    @Schema(description = "Where the prices behind savedEur came from. ESTIMATE when any part of the figure was priced from an estimated tariff rather than a contracted one, and also when no tariff was consulted at all. Never null: an amount derived from an estimate is not interchangeable with one derived from a real tariff.",
            example = "ESTIMATE")
    private final TariffSource tariffSource;

    @Schema(description = "The estimated energy-term price, before taxes, savedEur was priced with. Present only when the estimated price was used to price at least part of savedEur; null when every supply was priced with its contracted tariff, and when nothing was priced at all (no period to price, or no supplies).",
            types = {"object", "null"})
    private final EstimatedPriceResponse estimatedPrice;

    public MembershipPaybackResponse(MembershipPayback payback) {
        int cents = 2;
        this.investmentEur = round(payback.getInvestmentEur(), cents);
        this.savedEur = round(payback.getSavedEur(), cents);
        this.remainingEur = round(payback.getRemainingEur(), cents);
        // Already at its defining scale of four decimals; rounding it again here would be a second
        // opinion on a figure the domain has settled.
        this.progressRatio = payback.getProgressRatio();
        this.startDate = payback.getStartDate();
        this.estimatedRemainingMonths = payback.getEstimatedRemainingMonths();
        this.tariffSource = payback.getTariffSource();
        this.estimatedPrice = EstimatedPriceResponse.from(payback.getEstimatedPrice());
    }

    private static BigDecimal round(BigDecimal amount, int scale) {
        return amount == null ? null : amount.setScale(scale, RoundingMode.HALF_UP);
    }

    public BigDecimal getInvestmentEur() {
        return investmentEur;
    }

    public BigDecimal getSavedEur() {
        return savedEur;
    }

    public BigDecimal getRemainingEur() {
        return remainingEur;
    }

    public BigDecimal getProgressRatio() {
        return progressRatio;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public Integer getEstimatedRemainingMonths() {
        return estimatedRemainingMonths;
    }

    public TariffSource getTariffSource() {
        return tariffSource;
    }

    public EstimatedPriceResponse getEstimatedPrice() {
        return estimatedPrice;
    }
}
