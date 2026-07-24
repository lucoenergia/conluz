package org.lucoenergia.conluz.infrastructure.production.plant.deactivate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

@Schema(requiredProperties = {"coefficientIds"})
public class DeactivateCoefficientsBody {

    @NotEmpty
    @Schema(description = "The coefficients to revert to pending, all belonging to this agreement.")
    private List<UUID> coefficientIds;

    public List<UUID> getCoefficientIds() {
        return coefficientIds;
    }

    public void setCoefficientIds(List<UUID> coefficientIds) {
        this.coefficientIds = coefficientIds;
    }
}
