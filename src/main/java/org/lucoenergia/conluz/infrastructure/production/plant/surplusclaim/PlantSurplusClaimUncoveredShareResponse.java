package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimWarnings.UncoveredShare;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The days whose applied coefficients leave part of the plant's production unshared.
 */
@Schema(requiredProperties = {"largestShare", "days"})
public class PlantSurplusClaimUncoveredShareResponse {

    @Schema(description = "The largest share of the production, on a 0-1 scale, that the applied coefficients " +
            "leave without a supply on any of the days.", example = "0.08")
    private final BigDecimal largestShare;
    @Schema(description = "The local days whose applied coefficients, the plant's own supply included, add up to " +
            "less than 1, in order.", example = "[\"2026-06-01\"]")
    private final List<LocalDate> days;

    public PlantSurplusClaimUncoveredShareResponse(UncoveredShare uncoveredShare) {
        this.largestShare = uncoveredShare.largestShare();
        this.days = uncoveredShare.days();
    }

    public BigDecimal getLargestShare() {
        return largestShare;
    }

    public List<LocalDate> getDays() {
        return days;
    }
}
