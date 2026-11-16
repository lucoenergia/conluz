package org.lucoenergia.conluz.infrastructure.admin.supply.transfer;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(requiredProperties = {"newOwnerPersonalId", "effectiveDate"})
public class TransferSupplyOwnershipBody {

    @Schema(description = "The DNI/NIE/NIF of the member who becomes the supply's owner. It is normalised before the owner is looked up: whitespace (including the no-break space), dots and hyphens are removed and letters are upper-cased, so 12.345.678-a and 12345678A are the same identifier.")
    @NotBlank
    private String newOwnerPersonalId;

    @Schema(description = "The date the new owner became the supply's holder at the distributor. It cannot be later than today, and must be later than the date of the supply's previous transfer.",
            example = "2026-03-01")
    @NotNull
    private LocalDate effectiveDate;

    public String getNewOwnerPersonalId() {
        return newOwnerPersonalId;
    }

    public void setNewOwnerPersonalId(String newOwnerPersonalId) {
        this.newOwnerPersonalId = newOwnerPersonalId;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }
}
