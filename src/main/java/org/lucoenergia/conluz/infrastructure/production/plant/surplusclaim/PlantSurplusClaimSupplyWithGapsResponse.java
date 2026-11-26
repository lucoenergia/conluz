package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimWarnings.SupplyWithGaps;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.SupplyReferenceResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * A supply in scope with surplus records in the month, but not for every hour of some days.
 */
@Schema(requiredProperties = {"supply", "days"})
public class PlantSurplusClaimSupplyWithGapsResponse {

    @Schema(description = "The supply with gaps")
    private final SupplyReferenceResponse supply;
    @Schema(description = "The local days on which the supply misses a surplus record for some hour, in order.",
            example = "[\"2026-06-10\"]")
    private final List<LocalDate> days;

    public PlantSurplusClaimSupplyWithGapsResponse(SupplyWithGaps supplyWithGaps) {
        this.supply = new SupplyReferenceResponse(supplyWithGaps.supply().id(), supplyWithGaps.supply().code(),
                supplyWithGaps.supply().name());
        this.days = supplyWithGaps.days();
    }

    public SupplyReferenceResponse getSupply() {
        return supply;
    }

    public List<LocalDate> getDays() {
        return days;
    }
}
