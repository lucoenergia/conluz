package org.lucoenergia.conluz.infrastructure.production.plant;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.plant.SharingAgreement;
import org.lucoenergia.conluz.domain.production.plant.SharingAgreementStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class SharingAgreementResponse {

    @Schema(description = "Internal unique identifier of the sharing agreement", example = "ebbe60d1-f9db-455c-8c2d-c34ae7a1c23c")
    private final UUID id;
    @Schema(description = "Identifier of the plant this agreement distributes production from", example = "4b2f60d1-f9db-455c-8c2d-c34ae7a1c23c")
    private final UUID plantId;
    @Schema(description = "Human-readable label for the agreement", example = "2024 winter distribution")
    private final String name;
    @Schema(description = "Free-text notes about the agreement", example = "Adjusted after member B joined", nullable = true)
    private final String notes;
    @Schema(description = "Status of the agreement: DRAFT, PUBLISHED or SUPERSEDED", example = "PUBLISHED")
    private final SharingAgreementStatus status;
    @Schema(description = "Snapshot of the plant's installed power at authoring time, in kW", example = "12.5", nullable = true)
    private final BigDecimal installedPowerKw;
    @Schema(description = "Date and time the agreement was created")
    private final Instant createdAt;
    @Schema(description = "Identifier of the user who created the agreement. Null means it was created by the system (a migration), not by a person", nullable = true)
    private final UUID createdBy;

    public SharingAgreementResponse(SharingAgreement sharingAgreement) {
        this.id = sharingAgreement.getId();
        this.plantId = sharingAgreement.getPlantId();
        this.name = sharingAgreement.getName();
        this.notes = sharingAgreement.getNotes();
        this.status = sharingAgreement.getStatus();
        this.installedPowerKw = sharingAgreement.getInstalledPowerKw();
        this.createdAt = sharingAgreement.getCreatedAt();
        this.createdBy = sharingAgreement.getCreatedBy();
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlantId() {
        return plantId;
    }

    public String getName() {
        return name;
    }

    public String getNotes() {
        return notes;
    }

    public SharingAgreementStatus getStatus() {
        return status;
    }

    public BigDecimal getInstalledPowerKw() {
        return installedPowerKw;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
