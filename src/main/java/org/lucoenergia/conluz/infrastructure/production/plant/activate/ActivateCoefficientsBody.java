package org.lucoenergia.conluz.infrastructure.production.plant.activate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(requiredProperties = {"appliedOn", "coefficientIds"})
public class ActivateCoefficientsBody {

    @NotNull
    @Schema(description = "The date the distributor applied these coefficients (or the corrected " +
            "date, if any of them are already active). Backdating is allowed; future dates are rejected.",
            example = "2026-03-01")
    private LocalDate appliedOn;

    @NotEmpty
    @Schema(description = "The coefficients to activate or correct, all belonging to this agreement.")
    private List<UUID> coefficientIds;

    public LocalDate getAppliedOn() {
        return appliedOn;
    }

    public void setAppliedOn(LocalDate appliedOn) {
        this.appliedOn = appliedOn;
    }

    public List<UUID> getCoefficientIds() {
        return coefficientIds;
    }

    public void setCoefficientIds(List<UUID> coefficientIds) {
        this.coefficientIds = coefficientIds;
    }
}
