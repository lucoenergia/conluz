package org.lucoenergia.conluz.domain.production.sharingagreement.create;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The authorable fields of a new sharing agreement. Bundling them here, rather than passing them as
 * individual parameters to {@link CreateSharingAgreementService}/{@link CreateSharingAgreementRepository},
 * means adding or removing a field never changes those method signatures.
 */
public class CreateSharingAgreement {

    private final String name;
    private final String notes;
    private final BigDecimal installedPowerKw;
    private final UUID createdBy;

    private CreateSharingAgreement(Builder builder) {
        this.name = builder.name;
        this.notes = builder.notes;
        this.installedPowerKw = builder.installedPowerKw;
        this.createdBy = builder.createdBy;
    }

    public String getName() {
        return name;
    }

    public String getNotes() {
        return notes;
    }

    public BigDecimal getInstalledPowerKw() {
        return installedPowerKw;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public static class Builder {
        private String name;
        private String notes;
        private BigDecimal installedPowerKw;
        private UUID createdBy;

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withNotes(String notes) {
            this.notes = notes;
            return this;
        }

        public Builder withInstalledPowerKw(BigDecimal installedPowerKw) {
            this.installedPowerKw = installedPowerKw;
            return this;
        }

        public Builder withCreatedBy(UUID createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public CreateSharingAgreement build() {
            return new CreateSharingAgreement(this);
        }
    }
}
