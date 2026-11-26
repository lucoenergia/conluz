package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.energy.SurplusIncome;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The estimated income of a plant's claimed surplus, with the field names and rounding of the community energy
 * metrics' surplus income.
 */
@Schema(requiredProperties = {"amountEur", "unpricedSurplusKWh"})
public class PlantSurplusClaimSurplusIncomeResponse {

    @Schema(description = "Estimated income of the month's surplus, in euros, at OMIE day-ahead prices: each " +
            "hour's surplus multiplied by the mean of the prices stored for that hour, summed, then rounded to " +
            "cents. It is a market-price estimate, without taxes, fees, deviation costs or retailer margins. " +
            "Market prices may be zero or negative and are applied as they are, so the amount may be negative. " +
            "Hours without a stored price add nothing; their surplus is reported in unpricedSurplusKWh.",
            example = "18.42")
    private final BigDecimal amountEur;
    @Schema(description = "The part of summary.surplusKWh in hours without a stored OMIE price, which amountEur " +
            "does not include. In kWh, rounded to 3 decimals. 0 when every hour was priced.",
            example = "1.5")
    private final double unpricedSurplusKWh;

    public PlantSurplusClaimSurplusIncomeResponse(SurplusIncome income) {
        // Scale is declared inline rather than as a constant: ResponseSchemaNullabilityArchTest
        // inspects every field of a *Response class, static ones included.
        int cents = 2;
        this.amountEur = income.getAmountEur().setScale(cents, RoundingMode.HALF_UP);
        this.unpricedSurplusKWh = PlantSurplusClaimHourResponse.round(income.getUnpricedSurplusKWh());
    }

    public BigDecimal getAmountEur() {
        return amountEur;
    }

    public double getUnpricedSurplusKWh() {
        return unpricedSurplusKWh;
    }
}
