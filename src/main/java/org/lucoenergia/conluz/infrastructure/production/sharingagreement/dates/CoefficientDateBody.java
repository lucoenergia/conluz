package org.lucoenergia.conluz.infrastructure.production.sharingagreement.dates;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

@Schema(requiredProperties = {"coefficientId", "validFrom"})
public class CoefficientDateBody {

    @NotNull
    @Schema(description = "A coefficient of this agreement, pending (activated) or active (corrected).",
            example = "6f1f3c2e-1234-5678-abcd-000000000001")
    private UUID coefficientId;

    @NotNull
    @Schema(description = "First day the coefficient applies; stored as validFrom. Future dates are rejected.",
            example = "2026-03-01")
    private LocalDate validFrom;

    public UUID getCoefficientId() {
        return coefficientId;
    }

    public void setCoefficientId(UUID coefficientId) {
        this.coefficientId = coefficientId;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }
}
