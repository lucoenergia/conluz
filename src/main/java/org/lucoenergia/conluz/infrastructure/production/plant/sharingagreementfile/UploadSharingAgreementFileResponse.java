package org.lucoenergia.conluz.infrastructure.production.plant.sharingagreementfile;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.plant.sharingagreementfile.DistributorFileStoreResult;

import java.util.UUID;

public class UploadSharingAgreementFileResponse {

    @Schema(description = "Internal unique identifier of the stored file", example = "b3d1a2f0-1234-5678-abcd-000000000001")
    private final UUID fileId;

    @Schema(description = "Original uploaded filename", example = "ES0031300325733001FH0FA000_2024.txt")
    private final String filename;

    @Schema(description = "Number of coefficient entries materialised as pending rows", example = "12")
    private final int entriesMaterialized;

    public UploadSharingAgreementFileResponse(DistributorFileStoreResult result) {
        this.fileId = result.getFile().getId();
        this.filename = result.getFile().getFilename();
        this.entriesMaterialized = result.getEntries().size();
    }

    public UUID getFileId() {
        return fileId;
    }

    public String getFilename() {
        return filename;
    }

    public int getEntriesMaterialized() {
        return entriesMaterialized;
    }
}
