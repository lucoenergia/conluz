package org.lucoenergia.conluz.infrastructure.production.sharingagreement;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(requiredProperties = {"id", "filename", "uploadedAt"})
public class SharingAgreementFileResponse {

    @Schema(description = "Internal unique identifier of the stored file", example = "ebbe60d1-f9db-455c-8c2d-c34ae7a1c23c")
    private final UUID id;

    @Schema(description = "Original filename as uploaded", example = "distributor-2025.txt")
    private final String filename;

    @Schema(description = "Date and time the file was uploaded")
    private final Instant uploadedAt;

    public SharingAgreementFileResponse(UUID id, String filename, Instant uploadedAt) {
        this.id = id;
        this.filename = filename;
        this.uploadedAt = uploadedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
