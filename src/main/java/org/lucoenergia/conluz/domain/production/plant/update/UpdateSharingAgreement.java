package org.lucoenergia.conluz.domain.production.plant.update;

import java.math.BigDecimal;

/**
 * The updatable fields of a sharing agreement. Bundling them here, rather than passing them as
 * individual parameters to {@link UpdateSharingAgreementService}/{@link UpdateSharingAgreementRepository},
 * means adding or removing an updatable field never changes those method signatures.
 */
public class UpdateSharingAgreement {

    private final String name;
    private final String notes;
    private final BigDecimal installedPowerKw;

    private UpdateSharingAgreement(Builder builder) {
        this.name = builder.name;
        this.notes = builder.notes;
        this.installedPowerKw = builder.installedPowerKw;
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

    public static class Builder {
        private String name;
        private String notes;
        private BigDecimal installedPowerKw;

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

        public UpdateSharingAgreement build() {
            return new UpdateSharingAgreement(this);
        }
    }
}
