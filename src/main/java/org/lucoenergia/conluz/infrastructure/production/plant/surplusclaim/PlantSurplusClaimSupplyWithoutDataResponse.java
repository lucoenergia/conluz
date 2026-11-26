package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimWarnings.SupplyWithoutData;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.SupplyReferenceResponse;

import java.math.BigDecimal;

/**
 * A supply in scope without any surplus record in the month.
 */
@Schema(requiredProperties = {"supply", "coefficient"})
public class PlantSurplusClaimSupplyWithoutDataResponse {

    @Schema(description = "The supply without data")
    private final SupplyReferenceResponse supply;
    @Schema(description = "The supply's coefficient in the plant, on a 0-1 scale: the one in force on the last day " +
            "of the month on which the supply held one.", example = "0.030763")
    private final BigDecimal coefficient;

    public PlantSurplusClaimSupplyWithoutDataResponse(SupplyWithoutData supplyWithoutData) {
        this.supply = new SupplyReferenceResponse(supplyWithoutData.supply().id(), supplyWithoutData.supply().code(),
                supplyWithoutData.supply().name());
        this.coefficient = supplyWithoutData.coefficient();
    }

    public SupplyReferenceResponse getSupply() {
        return supply;
    }

    public BigDecimal getCoefficient() {
        return coefficient;
    }
}
