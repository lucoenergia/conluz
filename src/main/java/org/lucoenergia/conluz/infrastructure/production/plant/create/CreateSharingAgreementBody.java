package org.lucoenergia.conluz.infrastructure.production.plant.create;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(requiredProperties = {"name"})
public class CreateSharingAgreementBody {

    @Schema(description = "Human-readable label for the agreement", example = "2024 winter distribution")
    @NotBlank
    private String name;
    @Schema(description = "Free-text notes about the agreement", example = "Adjusted after member B joined")
    private String notes;

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
}
