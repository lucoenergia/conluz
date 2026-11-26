package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimWarnings;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;

/**
 * What the admin should know before claiming the month. None of them prevents the claim. A list is empty, never
 * null, when its warning is not raised.
 */
@Schema(requiredProperties = {"suppliesWithoutData", "suppliesWithGaps", "uncoveredShare", "plantSupplyShare",
        "noAppliedCoefficients", "hoursAboveNominalPower"})
public class PlantSurplusClaimWarningsResponse {

    @Schema(description = "The supplies in scope without any surplus record in the month, by supply code.")
    private final List<PlantSurplusClaimSupplyWithoutDataResponse> suppliesWithoutData;
    @Schema(description = "The supplies in scope with surplus records in the month, but not for every hour of " +
            "some days, by supply code.")
    private final List<PlantSurplusClaimSupplyWithGapsResponse> suppliesWithGaps;
    @Schema(description = "The days whose applied coefficients leave part of the production unshared. Null when " +
            "there is none, and when no applied coefficient overlaps the month, which noAppliedCoefficients " +
            "reports.", types = {"object", "null"})
    private final PlantSurplusClaimUncoveredShareResponse uncoveredShare;
    @Schema(description = "The coefficient, on a 0-1 scale, that the plant's own supply holds in the month: the " +
            "one in force on the last day of the month on which it held one. Its surplus is never claimed. Null " +
            "when it holds none.", example = "0.05", types = {"number", "null"})
    private final BigDecimal plantSupplyShare;
    @Schema(description = "Whether no applied coefficient of the plant overlaps the month, so that no supply is " +
            "in scope.", example = "false")
    private final boolean noAppliedCoefficients;
    @Schema(description = "The hours whose surplus is above what the plant's nominal power produces in one hour, " +
            "in order.")
    private final List<PlantSurplusClaimHourResponse> hoursAboveNominalPower;

    public PlantSurplusClaimWarningsResponse(SurplusClaimWarnings warnings, ZoneId zone) {
        this.suppliesWithoutData = warnings.suppliesWithoutData().stream()
                .map(PlantSurplusClaimSupplyWithoutDataResponse::new)
                .toList();
        this.suppliesWithGaps = warnings.suppliesWithGaps().stream()
                .map(PlantSurplusClaimSupplyWithGapsResponse::new)
                .toList();
        this.uncoveredShare = warnings.uncoveredShare() == null
                ? null
                : new PlantSurplusClaimUncoveredShareResponse(warnings.uncoveredShare());
        this.plantSupplyShare = warnings.plantSupplyShare();
        this.noAppliedCoefficients = warnings.noAppliedCoefficients();
        this.hoursAboveNominalPower = warnings.hoursAboveNominalPower().stream()
                .map(hour -> new PlantSurplusClaimHourResponse(hour, zone))
                .toList();
    }

    public List<PlantSurplusClaimSupplyWithoutDataResponse> getSuppliesWithoutData() {
        return suppliesWithoutData;
    }

    public List<PlantSurplusClaimSupplyWithGapsResponse> getSuppliesWithGaps() {
        return suppliesWithGaps;
    }

    public PlantSurplusClaimUncoveredShareResponse getUncoveredShare() {
        return uncoveredShare;
    }

    public BigDecimal getPlantSupplyShare() {
        return plantSupplyShare;
    }

    public boolean isNoAppliedCoefficients() {
        return noAppliedCoefficients;
    }

    public List<PlantSurplusClaimHourResponse> getHoursAboveNominalPower() {
        return hoursAboveNominalPower;
    }
}
