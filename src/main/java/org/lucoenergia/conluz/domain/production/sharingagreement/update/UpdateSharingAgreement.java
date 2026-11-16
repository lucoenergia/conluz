package org.lucoenergia.conluz.domain.production.sharingagreement.update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The updatable fields of a sharing agreement. Bundling them here, rather than passing them as
 * individual parameters to {@link UpdateSharingAgreementService}/{@link UpdateSharingAgreementRepository},
 * means adding or removing an updatable field never changes those method signatures.
 */
public class UpdateSharingAgreement {

    private final String name;
    private final String notes;
    private final BigDecimal nominalPowerKW;
    private final LocalDate distributorFileSentOn;
    private final UUID updatedBy;

    private UpdateSharingAgreement(Builder builder) {
        this.name = builder.name;
        this.notes = builder.notes;
        this.nominalPowerKW = builder.nominalPowerKW;
        this.distributorFileSentOn = builder.distributorFileSentOn;
        this.updatedBy = builder.updatedBy;
    }

    public String getName() {
        return name;
    }

    public String getNotes() {
        return notes;
    }

    public BigDecimal getNominalPowerKW() {
        return nominalPowerKW;
    }

    public LocalDate getDistributorFileSentOn() {
        return distributorFileSentOn;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public static class Builder {
        private String name;
        private String notes;
        private BigDecimal nominalPowerKW;
        private LocalDate distributorFileSentOn;
        private UUID updatedBy;

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withNotes(String notes) {
            this.notes = notes;
            return this;
        }

        public Builder withNominalPowerKW(BigDecimal nominalPowerKW) {
            this.nominalPowerKW = nominalPowerKW;
            return this;
        }

        public Builder withDistributorFileSentOn(LocalDate distributorFileSentOn) {
            this.distributorFileSentOn = distributorFileSentOn;
            return this;
        }

        public Builder withUpdatedBy(UUID updatedBy) {
            this.updatedBy = updatedBy;
            return this;
        }

        public UpdateSharingAgreement build() {
            return new UpdateSharingAgreement(this);
        }
    }
}
