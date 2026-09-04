package org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight metadata about the latest evidence file uploaded for a sharing agreement, without its
 * content -- used to render the file's presence, name and upload date without ever loading the
 * underlying {@code content} bytes.
 */
public class SharingAgreementFileSummary {

    private final UUID id;
    private final String filename;
    private final Instant uploadedAt;

    private SharingAgreementFileSummary(Builder builder) {
        this.id = builder.id;
        this.filename = builder.filename;
        this.uploadedAt = builder.uploadedAt;
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

    public static class Builder {
        private UUID id;
        private String filename;
        private Instant uploadedAt;

        public Builder withId(UUID id) {
            this.id = id;
            return this;
        }

        public Builder withFilename(String filename) {
            this.filename = filename;
            return this;
        }

        public Builder withUploadedAt(Instant uploadedAt) {
            this.uploadedAt = uploadedAt;
            return this;
        }

        public SharingAgreementFileSummary build() {
            return new SharingAgreementFileSummary(this);
        }
    }
}
