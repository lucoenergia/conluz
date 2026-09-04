package org.lucoenergia.conluz.domain.production.sharingagreement;

import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.SharingAgreementFileSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A sharing agreement governs how a plant's production is distributed among its community
 * members. {@code createdBy} is {@code null} when the agreement was created by the system
 * (a migration), not by a person.
 */
public class SharingAgreement {

    private final UUID id;
    private final UUID plantId;
    private final String name;
    private final String notes;
    private final SharingAgreementStatus status;
    private final BigDecimal installedPowerKw;
    private final Instant createdAt;
    private final UUID createdBy;
    private final SharingAgreementFileSummary file;

    private SharingAgreement(Builder builder) {
        this.id = builder.id;
        this.plantId = builder.plantId;
        this.name = builder.name;
        this.notes = builder.notes;
        this.status = builder.status;
        this.installedPowerKw = builder.installedPowerKw;
        this.createdAt = builder.createdAt;
        this.createdBy = builder.createdBy;
        this.file = builder.file;
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

    public SharingAgreementFileSummary getFile() {
        return file;
    }

    /**
     * Returns a copy of this agreement with its {@code file} replaced. Used to attach the latest
     * uploaded file's metadata after loading/saving the agreement itself, keeping that lookup out of
     * {@code SharingAgreementEntityMapper} so a list of agreements can be enriched with a single
     * batched query instead of one lookup per row.
     */
    public SharingAgreement withFile(SharingAgreementFileSummary file) {
        return new Builder()
                .withId(id)
                .withPlantId(plantId)
                .withName(name)
                .withNotes(notes)
                .withStatus(status)
                .withInstalledPowerKw(installedPowerKw)
                .withCreatedAt(createdAt)
                .withCreatedBy(createdBy)
                .withFile(file)
                .build();
    }

    /**
     * Throws {@link SharingAgreementNotDraftException} unless this agreement is still DRAFT. Used
     * by patch/delete/publish to keep a PUBLISHED agreement's data (including the installed-power
     * snapshot) an immutable historical record.
     */
    public void assertDraft() {
        if (status != SharingAgreementStatus.DRAFT) {
            throw new SharingAgreementNotDraftException(id, status);
        }
    }

    /**
     * Throws {@link SharingAgreementNotPublishedException} only for DRAFT; PUBLISHED and SUPERSEDED
     * both pass. Used by the coefficient-activation paths (activate/deactivate/close/reopen), which
     * deliberately do NOT call {@link #assertDraft()}: that guard protects WHICH coefficients exist
     * (sealed at publish), while activation records WHEN an external party applied one -- an observed
     * fact that can occur after publication, and must remain correctable even once this agreement
     * becomes SUPERSEDED (e.g. fixing a mis-recorded date on one of its own rows). SUPERSEDED is a
     * recomputed, not authored, status; freezing it here would make such corrections permanently
     * impossible, which nothing in the spec calls for. This is the one write on a non-DRAFT agreement
     * that is correct -- do not "fix" it to require DRAFT.
     */
    public void assertNotDraft() {
        if (status == SharingAgreementStatus.DRAFT) {
            throw new SharingAgreementNotPublishedException(id, status);
        }
    }

    /**
     * Throws {@link SharingAgreementNotRevertibleException} unless this agreement is PUBLISHED. Used
     * by revert-to-draft: DRAFT (already draft) and SUPERSEDED (every coefficient already applied) are
     * both rejected, since only a PUBLISHED-but-still-inert agreement can safely go back to DRAFT.
     */
    public void assertPublished() {
        if (status != SharingAgreementStatus.PUBLISHED) {
            throw new SharingAgreementNotRevertibleException(id, status);
        }
    }

    public static class Builder {
        private UUID id;
        private UUID plantId;
        private String name;
        private String notes;
        private SharingAgreementStatus status;
        private BigDecimal installedPowerKw;
        private Instant createdAt;
        private UUID createdBy;
        private SharingAgreementFileSummary file;

        public Builder withId(UUID id) {
            this.id = id;
            return this;
        }

        public Builder withPlantId(UUID plantId) {
            this.plantId = plantId;
            return this;
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withNotes(String notes) {
            this.notes = notes;
            return this;
        }

        public Builder withStatus(SharingAgreementStatus status) {
            this.status = status;
            return this;
        }

        public Builder withInstalledPowerKw(BigDecimal installedPowerKw) {
            this.installedPowerKw = installedPowerKw;
            return this;
        }

        public Builder withCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder withCreatedBy(UUID createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Builder withFile(SharingAgreementFileSummary file) {
            this.file = file;
            return this;
        }

        public SharingAgreement build() {
            return new SharingAgreement(this);
        }
    }
}
