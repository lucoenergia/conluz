package org.lucoenergia.conluz.infrastructure.production.plant.reopen;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

@Schema(requiredProperties = {"coefficientIds"})
public class ReopenCoefficientsBody {

    @NotEmpty
    @Schema(description = "The coefficients to reopen (retract an authored close), all belonging to " +
            "this agreement. Rejected when the close was written by the activation cascade rather " +
            "than authored.")
    private List<UUID> coefficientIds;

    public List<UUID> getCoefficientIds() {
        return coefficientIds;
    }

    public void setCoefficientIds(List<UUID> coefficientIds) {
        this.coefficientIds = coefficientIds;
    }
}
