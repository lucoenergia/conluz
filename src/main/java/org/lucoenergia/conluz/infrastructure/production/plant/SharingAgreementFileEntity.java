package org.lucoenergia.conluz.infrastructure.production.plant;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * No domain object, mapper, service or controller exists for this entity yet -- those arrive in
 * phase 5, once there is a real consumer.
 */
@Entity(name = "sharing_agreement_file")
public class SharingAgreementFileEntity {

    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sharing_agreement_id")
    private SharingAgreementEntity sharingAgreement;
    private String filename;
    @Lob
    private byte[] content;
    @Column(name = "content_hash")
    private String contentHash;
    @Column(name = "uploaded_at")
    private Instant uploadedAt;
    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public SharingAgreementEntity getSharingAgreement() {
        return sharingAgreement;
    }

    public void setSharingAgreement(SharingAgreementEntity sharingAgreement) {
        this.sharingAgreement = sharingAgreement;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(UUID uploadedBy) {
        this.uploadedBy = uploadedBy;
    }
}
