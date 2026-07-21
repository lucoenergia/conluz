package org.lucoenergia.conluz.domain.production.plant;

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

    private SharingAgreement(Builder builder) {
        this.id = builder.id;
        this.plantId = builder.plantId;
        this.name = builder.name;
        this.notes = builder.notes;
        this.status = builder.status;
        this.installedPowerKw = builder.installedPowerKw;
        this.createdAt = builder.createdAt;
        this.createdBy = builder.createdBy;
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

    public static class Builder {
        private UUID id;
        private UUID plantId;
        private String name;
        private String notes;
        private SharingAgreementStatus status;
        private BigDecimal installedPowerKw;
        private Instant createdAt;
        private UUID createdBy;

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

        public SharingAgreement build() {
            return new SharingAgreement(this);
        }
    }
}
