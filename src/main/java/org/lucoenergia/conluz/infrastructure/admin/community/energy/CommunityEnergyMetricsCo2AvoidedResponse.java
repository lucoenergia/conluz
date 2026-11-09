package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyMetrics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The CO₂ the community's plants avoided, and the years of the emission factors applied.
 *
 * <p>This is where the amount is rounded, once, to 3 decimals, half-up: the calculation sums every year
 * at full precision.
 */
@Schema(requiredProperties = {"amountKg", "factorYears"})
public class CommunityEnergyMetricsCo2AvoidedResponse {

    @Schema(description = "Estimated CO₂ avoided by the community's plants over the period, in kg CO₂e: " +
            "the emissions that the same electricity would have caused had it been bought from a retailer " +
            "without guarantees of origin. It uses the MITECO \"Mix sin GdO\" electricity emission factors, " +
            "not the average grid mix. The production of each calendar year, in the community's time zone, " +
            "is multiplied by that year's factor, or by the latest earlier year's factor when its own is " +
            "not published yet; the products are summed, then rounded to 3 decimals. Null when " +
            "energy.productionKWh is null, or when a year with production is earlier than every " +
            "available factor.",
            example = "392.16", types = {"number", "null"})
    private final BigDecimal amountKg;
    @Schema(description = "The years of the emission factors applied to amountKg, distinct and in " +
            "ascending order. A year later than the last published factor shows that earlier year. Null " +
            "exactly when amountKg is null.",
            types = {"array", "null"})
    private final List<Integer> factorYears;

    public CommunityEnergyMetricsCo2AvoidedResponse(CommunityEnergyMetrics metrics) {
        // Scale is declared inline rather than as a constant: ResponseSchemaNullabilityArchTest
        // inspects every field of a *Response class, static ones included.
        int decimals = 3;
        this.amountKg = metrics.getCo2AvoidedKg() == null
                ? null
                : metrics.getCo2AvoidedKg().setScale(decimals, RoundingMode.HALF_UP);
        this.factorYears = metrics.getCo2AvoidedFactorYears();
    }

    public BigDecimal getAmountKg() {
        return amountKg;
    }

    public List<Integer> getFactorYears() {
        return factorYears;
    }
}
