package org.lucoenergia.conluz.infrastructure.production.plant.update;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.lucoenergia.conluz.domain.production.plant.update.UpdateSharingAgreement;

import java.math.BigDecimal;

@Schema(requiredProperties = {"name", "installedPowerKw"})
public class UpdateSharingAgreementBody {

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

    public UpdateSharingAgreement mapToUpdateSharingAgreement() {
        return new UpdateSharingAgreement.Builder()
                .withName(name)
                .withNotes(notes)
                .withInstalledPowerKw(installedPowerKw)
                .build();
    }
}
