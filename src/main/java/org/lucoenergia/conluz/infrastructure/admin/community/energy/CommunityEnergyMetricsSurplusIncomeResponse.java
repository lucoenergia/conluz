package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyMetrics;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What the community's surplus earned at OMIE day-ahead prices, and the surplus that could not be
 * priced.
 *
 * <p>This is where both figures are rounded, once, for presentation: the amount to cents, half-up, as
 * savings amounts are, and the energy to 3 decimals. The calculation sums every hour at full precision.
 */
@Schema(requiredProperties = {"amountEur", "unpricedSurplusKWh"})
public class CommunityEnergyMetricsSurplusIncomeResponse {

    @Schema(description = "Estimated income of the surplus over the period, in euros, at OMIE day-ahead " +
            "prices (the Spanish system's marginal price): each hour's surplus multiplied by the mean of " +
            "the prices stored for that hour, summed, then rounded to cents. It is a market-price " +
            "estimate, without taxes, fees, deviation costs or retailer margins. Market prices may be zero " +
            "or negative and are applied as they are, so the amount may be negative. Hours without a " +
            "stored price add nothing; their surplus is reported in unpricedSurplusKWh. Null when " +
            "energy.surplusKWh is null.",
            example = "18.42", types = {"number", "null"})
    private final BigDecimal amountEur;
    @Schema(description = "The part of energy.surplusKWh in hours without a stored OMIE price, which " +
            "amountEur does not include. In kWh, rounded to 3 decimals. 0 when every hour was priced. " +
            "Null when energy.surplusKWh is null.",
            example = "1.5", types = {"number", "null"})
    private final Double unpricedSurplusKWh;

    public CommunityEnergyMetricsSurplusIncomeResponse(CommunityEnergyMetrics metrics) {
        // Scale is declared inline rather than as a constant: ResponseSchemaNullabilityArchTest
        // inspects every field of a *Response class, static ones included, and would demand a
        // @Schema marker on a private constant that is not part of the contract at all.
        int cents = 2;
        this.amountEur = metrics.getSurplusIncomeEur() == null
                ? null
                : metrics.getSurplusIncomeEur().setScale(cents, RoundingMode.HALF_UP);
        this.unpricedSurplusKWh = CommunityEnergyMetricsEnergyResponse.round(metrics.getUnpricedSurplusKWh());
    }

    public BigDecimal getAmountEur() {
        return amountEur;
    }

    public Double getUnpricedSurplusKWh() {
        return unpricedSurplusKWh;
    }
}
