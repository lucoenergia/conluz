package org.lucoenergia.conluz.infrastructure.production.sharingagreement.update;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.lucoenergia.conluz.domain.production.sharingagreement.update.UpdateSharingAgreement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(requiredProperties = {"name", "nominalPowerKW"})
public class UpdateSharingAgreementBody {

    @Schema(description = "Human-readable label for the agreement", example = "Reparto 2025-2026")
    @NotBlank
    private String name;
    @Schema(description = "Free-text notes about the agreement", example = "Adjusted after member B joined")
    private String notes;
    @Schema(description = "Snapshot of the plant's nominal power at authoring time, in kW.", example = "12.5")
    @NotNull
    @Positive
    private BigDecimal nominalPowerKW;
    @Schema(description = "Day the agreement's distributor file was sent to the distributor (local date). Optional. "
            + "Lower bound from which the distributor is expected to apply the coefficients.",
            example = "2026-03-01", types = {"string", "null"})
    private LocalDate distributorFileSentOn;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public BigDecimal getNominalPowerKW() {
        return nominalPowerKW;
    }

    public void setNominalPowerKW(BigDecimal nominalPowerKW) {
        this.nominalPowerKW = nominalPowerKW;
    }

    public LocalDate getDistributorFileSentOn() {
        return distributorFileSentOn;
    }

    public void setDistributorFileSentOn(LocalDate distributorFileSentOn) {
        this.distributorFileSentOn = distributorFileSentOn;
    }

    public UpdateSharingAgreement mapToUpdateSharingAgreement(UUID updatedBy) {
        return new UpdateSharingAgreement.Builder()
                .withName(name)
                .withNotes(notes)
                .withNominalPowerKW(nominalPowerKW)
                .withDistributorFileSentOn(distributorFileSentOn)
                .withUpdatedBy(updatedBy)
                .build();
    }
}
