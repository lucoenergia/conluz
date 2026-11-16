package org.lucoenergia.conluz.infrastructure.production.sharingagreement.close;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(requiredProperties = {"closedOn", "coefficientIds"})
public class CloseCoefficientsBody {

    @NotNull
    @Schema(description = "First day the coefficient no longer applies (exclusive); stored as validTo. " +
            "Used for the exit case (the supply left the distribution), or as the corrected date if it " +
            "was already closed by an earlier call.",
            example = "2026-03-01")
    private LocalDate closedOn;

    @NotEmpty
    @Schema(description = "The coefficients to close or correct, all belonging to this agreement.")
    private List<UUID> coefficientIds;

    public LocalDate getClosedOn() {
        return closedOn;
    }

    public void setClosedOn(LocalDate closedOn) {
        this.closedOn = closedOn;
    }

    public List<UUID> getCoefficientIds() {
        return coefficientIds;
    }

    public void setCoefficientIds(List<UUID> coefficientIds) {
        this.coefficientIds = coefficientIds;
    }
}
