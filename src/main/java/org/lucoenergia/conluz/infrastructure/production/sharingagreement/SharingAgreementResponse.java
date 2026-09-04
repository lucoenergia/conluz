package org.lucoenergia.conluz.infrastructure.production.sharingagreement;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;

@Schema(requiredProperties = {"id", "plantId", "name", "notes", "status", "installedPowerKw", "createdAt", "createdBy", "file"})
public class SharingAgreementResponse {

    @Schema(description = "Internal unique identifier of the sharing agreement", example = "ebbe60d1-f9db-455c-8c2d-c34ae7a1c23c")
    private final UUID id;
    @Schema(description = "Identifier of the plant this agreement distributes production from", example = "4b2f60d1-f9db-455c-8c2d-c34ae7a1c23c")
    private final UUID plantId;
    @Schema(description = "Human-readable label for the agreement", example = "Reparto 2025-2026")
    private final String name;
    @Schema(description = "Free-text notes about the agreement", example = "Adjusted after member B joined", types = {"string", "null"})
    private final String notes;
    @Schema(description = "Status of the agreement: DRAFT, PUBLISHED or SUPERSEDED", example = "PUBLISHED")
    private final SharingAgreementStatus status;
    @Schema(description = "Snapshot of the plant's installed power at authoring time, in kW", example = "12.5")
    private final BigDecimal installedPowerKw;
    @Schema(description = "Date and time the agreement was created")
    private final Instant createdAt;
    @Schema(description = "Identifier of the user who created the agreement. Null means it was created by the system (a migration), not by a person", types = {"string", "null"})
    private final UUID createdBy;
    @Schema(description = "Metadata of the latest evidence file uploaded for this agreement. Null means no file has been uploaded, never that it wasn't loaded", types = {"object", "null"})
    private final SharingAgreementFileResponse file;

    public SharingAgreementResponse(SharingAgreement sharingAgreement) {
        this.id = sharingAgreement.getId();
        this.plantId = sharingAgreement.getPlantId();
        this.name = sharingAgreement.getName();
        this.notes = sharingAgreement.getNotes();
        this.status = sharingAgreement.getStatus();
        this.installedPowerKw = sharingAgreement.getInstalledPowerKw();
        this.createdAt = sharingAgreement.getCreatedAt();
        this.createdBy = sharingAgreement.getCreatedBy();
        this.file = sharingAgreement.getFile() == null ? null : new SharingAgreementFileResponse(
                sharingAgreement.getFile().getId(),
                sharingAgreement.getFile().getFilename(),
                sharingAgreement.getFile().getUploadedAt());
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

    public SharingAgreementFileResponse getFile() {
        return file;
    }
}
