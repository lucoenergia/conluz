package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.PlantSurplusClaim;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.PlantReferenceResponse;

import java.util.List;

/**
 * A plant's surplus for one closed month, hour by hour, as it would be claimed from the retailer, with what is
 * missing from it.
 */
@Schema(requiredProperties = {"month", "plant", "cil", "availableMonths", "summary", "days", "warnings"})
public class PlantSurplusClaimResponse {

    @Schema(description = "The claimed month, as YYYY-MM, in the community's time zone.", example = "2026-09")
    private final String month;
    @Schema(description = "The plant whose surplus is claimed")
    private final PlantReferenceResponse plant;
    @Schema(description = "The plant's CIL (Codigo de Instalacion de produccion a efectos de Liquidacion), the " +
            "settlement code the retailer identifies the installation by. Null when the plant has none recorded.",
            types = {"string", "null"})
    private final String cil;
    @Schema(description = "The range of months the plant's surplus can be claimed for")
    private final PlantSurplusClaimAvailableMonthsResponse availableMonths;
    @Schema(description = "The month's totals")
    private final PlantSurplusClaimSummaryResponse summary;
    @Schema(description = "Every local day of the month, in order")
    private final List<PlantSurplusClaimDayResponse> days;
    @Schema(description = "What the admin should know before claiming the month")
    private final PlantSurplusClaimWarningsResponse warnings;

    public PlantSurplusClaimResponse(PlantSurplusClaim claim) {
        this.month = PlantSurplusClaimAvailableMonthsResponse.format(claim.month());
        this.plant = new PlantReferenceResponse(claim.plant().id(), claim.plant().name());
        this.cil = claim.cil();
        this.availableMonths = new PlantSurplusClaimAvailableMonthsResponse(claim.availableMonths());
        this.summary = new PlantSurplusClaimSummaryResponse(claim.summary());
        this.days = claim.days().stream().map(day -> new PlantSurplusClaimDayResponse(day, claim.zone())).toList();
        this.warnings = new PlantSurplusClaimWarningsResponse(claim.warnings(), claim.zone());
    }

    public String getMonth() {
        return month;
    }

    public PlantReferenceResponse getPlant() {
        return plant;
    }

    public String getCil() {
        return cil;
    }

    public PlantSurplusClaimAvailableMonthsResponse getAvailableMonths() {
        return availableMonths;
    }

    public PlantSurplusClaimSummaryResponse getSummary() {
        return summary;
    }

    public List<PlantSurplusClaimDayResponse> getDays() {
        return days;
    }

    public PlantSurplusClaimWarningsResponse getWarnings() {
        return warnings;
    }
}
