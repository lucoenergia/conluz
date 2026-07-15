package org.lucoenergia.conluz.domain.production.plant.sharingagreementfile;

import java.time.Instant;
import java.util.UUID;

/**
 * Evidence file uploaded for a {@code sharing_agreement}: the original distributor file, kept
 * verbatim so it can be downloaded again byte-identical.
 */
public class SharingAgreementFile {

    private final UUID id;
    private final UUID sharingAgreementId;
    private final String filename;
    private final byte[] content;
    private final String contentHash;
    private final Instant uploadedAt;
    private final UUID uploadedBy;

    private SharingAgreementFile(Builder builder) {
        this.id = builder.id;
        this.sharingAgreementId = builder.sharingAgreementId;
        this.filename = builder.filename;
        this.content = builder.content;
        this.contentHash = builder.contentHash;
        this.uploadedAt = builder.uploadedAt;
        this.uploadedBy = builder.uploadedBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSharingAgreementId() {
        return sharingAgreementId;
    }

    public String getFilename() {
        return filename;
    }

    public byte[] getContent() {
        return content;
    }

    public String getContentHash() {
        return contentHash;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public static class Builder {
        private UUID id;
        private UUID sharingAgreementId;
        private String filename;
        private byte[] content;
        private String contentHash;
        private Instant uploadedAt;
        private UUID uploadedBy;

        public Builder withId(UUID id) {
            this.id = id;
            return this;
        }

        public Builder withSharingAgreementId(UUID sharingAgreementId) {
            this.sharingAgreementId = sharingAgreementId;
            return this;
        }

        public Builder withFilename(String filename) {
            this.filename = filename;
            return this;
        }

        public Builder withContent(byte[] content) {
            this.content = content;
            return this;
        }

        public Builder withContentHash(String contentHash) {
            this.contentHash = contentHash;
            return this;
        }

        public Builder withUploadedAt(Instant uploadedAt) {
            this.uploadedAt = uploadedAt;
            return this;
        }

        public Builder withUploadedBy(UUID uploadedBy) {
            this.uploadedBy = uploadedBy;
            return this;
        }

        public SharingAgreementFile build() {
            return new SharingAgreementFile(this);
        }
    }
}
