package org.lucoenergia.conluz.infrastructure.production.sharingagreement.create;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreement;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(requiredProperties = {"name", "installedPowerKw"})
public class CreateSharingAgreementBody {

    @Schema(description = "Human-readable label for the agreement", example = "2024 winter distribution")
    @NotBlank
    private String name;
    @Schema(description = "Free-text notes about the agreement", example = "Adjusted after member B joined")
    private String notes;
    @Schema(description = "Snapshot of the plant's installed power at authoring time, in kW", example = "12.5")
    @NotNull
    @Positive
    private BigDecimal installedPowerKw;

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

    public BigDecimal getInstalledPowerKw() {
        return installedPowerKw;
    }

    public void setInstalledPowerKw(BigDecimal installedPowerKw) {
        this.installedPowerKw = installedPowerKw;
    }

    public CreateSharingAgreement mapToCreateSharingAgreement(UUID createdBy) {
        return new CreateSharingAgreement.Builder()
                .withName(name)
                .withNotes(notes)
                .withInstalledPowerKw(installedPowerKw)
                .withCreatedBy(createdBy)
                .build();
    }
}
