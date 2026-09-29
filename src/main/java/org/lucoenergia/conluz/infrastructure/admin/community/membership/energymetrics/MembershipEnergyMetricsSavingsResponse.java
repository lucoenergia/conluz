package org.lucoenergia.conluz.infrastructure.admin.community.membership.energymetrics;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;
import org.lucoenergia.conluz.domain.consumption.SupplySavings;
import org.lucoenergia.conluz.infrastructure.admin.supply.consumption.EstimatedPriceResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What the self-consumed energy of the membership's supplies was worth over the period, and where
 * the prices behind that figure came from.
 *
 * <p>This is where the amount is rounded, once, for presentation: the per-supply amounts are summed
 * unrounded, so the total is not rounded once per supply.
 */
@Schema(requiredProperties = {"amountEur", "tariffSource", "estimatedPrice"})
public class MembershipEnergyMetricsSavingsResponse {

    @Schema(description = "Estimated amount saved over the period by every supply of the membership, " +
            "in euros, rounded to cents. Null only when no period could be resolved; whenever a " +
            "period exists it is a figure, 0.00 when nothing was priced.",
            example = "72.75", types = {"number", "null"})
    private final BigDecimal amountEur;
    @Schema(description = "Whether the prices behind the amount are the supplies' contracted tariffs " +
            "or an estimate. A single estimated stretch of any supply makes the whole amount an " +
            "estimate.",
            example = "ESTIMATE")
    private final TariffSource tariffSource;
    @Schema(description = "The estimated energy-term price, before taxes, the amount was priced " +
            "with. Present only when the estimated price was used to price at least part of the " +
            "period for at least one supply; null when every supply was priced with its contracted " +
            "tariff, and when nothing was priced with the estimate at all.",
            types = {"object", "null"})
    private final EstimatedPriceResponse estimatedPrice;

    public MembershipEnergyMetricsSavingsResponse(SupplySavings savings) {
        // Scale is declared inline rather than as a constant: ResponseSchemaNullabilityArchTest
        // inspects every field of a *Response class, static ones included.
        int cents = 2;
        this.amountEur = savings.getAmountEur() == null
                ? null
                : savings.getAmountEur().setScale(cents, RoundingMode.HALF_UP);
        this.tariffSource = savings.getTariffSource();
        this.estimatedPrice = EstimatedPriceResponse.from(savings.getEstimatedPrice());
    }

    public BigDecimal getAmountEur() {
        return amountEur;
    }

    public TariffSource getTariffSource() {
        return tariffSource;
    }

    public EstimatedPriceResponse getEstimatedPrice() {
        return estimatedPrice;
    }
}
