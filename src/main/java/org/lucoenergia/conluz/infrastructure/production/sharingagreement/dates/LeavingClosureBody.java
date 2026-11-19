package org.lucoenergia.conluz.infrastructure.production.sharingagreement.dates;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

@Schema(requiredProperties = {"supplyId", "closedOn"})
public class LeavingClosureBody {

    @NotNull
    @Schema(description = "A supply of the plant that has no coefficient in this agreement.",
            example = "ebbe60d1-f9db-455c-8c2d-c34ae7a1c23c")
    private UUID supplyId;

    @NotNull
    @Schema(description = "First day the supply's coefficient before this agreement no longer applies; stored as " +
            "its validTo. Future dates are rejected.", example = "2026-03-01")
    private LocalDate closedOn;

    public UUID getSupplyId() {
        return supplyId;
    }

    public void setSupplyId(UUID supplyId) {
        this.supplyId = supplyId;
    }

    public LocalDate getClosedOn() {
        return closedOn;
    }

    public void setClosedOn(LocalDate closedOn) {
        this.closedOn = closedOn;
    }
}
