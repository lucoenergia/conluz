package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimSummary;

/**
 * The totals of a plant's surplus claim over the month. Every figure is summed unrounded and rounded once, here.
 */
@Schema(requiredProperties = {"surplusKWh", "realDays", "estimatedDays", "suppliesInScope", "suppliesWithData",
        "surplusIncome"})
public class PlantSurplusClaimSummaryResponse {

    @Schema(description = "Surplus of the month's hours, summed, in kWh, rounded to 3 decimals. Null when no " +
            "supply in scope stored a surplus record in the month.",
            example = "4521.25", types = {"number", "null"})
    private final Double surplusKWh;
    @Schema(description = "Number of REAL days in the month.", example = "28")
    private final int realDays;
    @Schema(description = "Number of ESTIMATED days in the month.", example = "2")
    private final int estimatedDays;
    @Schema(description = "Number of supplies whose surplus the claim sums.", example = "12")
    private final int suppliesInScope;
    @Schema(description = "Number of those supplies that stored at least one surplus record in the month.",
            example = "11")
    private final int suppliesWithData;
    @Schema(description = "Estimated income of exactly the month's hourly surplus. Null when surplusKWh is null.",
            types = {"object", "null"})
    private final PlantSurplusClaimSurplusIncomeResponse surplusIncome;

    public PlantSurplusClaimSummaryResponse(SurplusClaimSummary summary) {
        this.surplusKWh = PlantSurplusClaimHourResponse.round(summary.surplusKWh());
        this.realDays = summary.realDays();
        this.estimatedDays = summary.estimatedDays();
        this.suppliesInScope = summary.suppliesInScope();
        this.suppliesWithData = summary.suppliesWithData();
        this.surplusIncome = summary.surplusIncome() == null
                ? null
                : new PlantSurplusClaimSurplusIncomeResponse(summary.surplusIncome());
    }

    public Double getSurplusKWh() {
        return surplusKWh;
    }

    public int getRealDays() {
        return realDays;
    }

    public int getEstimatedDays() {
        return estimatedDays;
    }

    public int getSuppliesInScope() {
        return suppliesInScope;
    }

    public int getSuppliesWithData() {
        return suppliesWithData;
    }

    public PlantSurplusClaimSurplusIncomeResponse getSurplusIncome() {
        return surplusIncome;
    }
}
