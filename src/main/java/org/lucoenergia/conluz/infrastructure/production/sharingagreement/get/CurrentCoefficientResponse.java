package org.lucoenergia.conluz.infrastructure.production.sharingagreement.get;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientDetail;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.SharingAgreementReferenceResponse;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The coefficient a supply is actually on right now, in the plant of the agreement being viewed --
 * what an administrator editing a draft needs in order to see what each supply's allocation would
 * change from.
 */
@Schema(requiredProperties = {"coefficient", "validFrom", "sharingAgreement"})
public class CurrentCoefficientResponse {

    @Schema(description = "The coefficient value currently in force, on a 0-1 scale", example = "0.030763")
    private final BigDecimal coefficient;

    @Schema(description = "When this coefficient took effect (inclusive)", example = "2024-05-23T00:00:00Z")
    private final Instant validFrom;

    @Schema(description = "The agreement that authored the coefficient currently in force. May be the " +
            "agreement being viewed, or an earlier one.")
    private final SharingAgreementReferenceResponse sharingAgreement;

    public CurrentCoefficientResponse(SupplyPartitionCoefficientDetail detail) {
        this.coefficient = detail.getCoefficientValue();
        this.validFrom = detail.getValidFrom();
        this.sharingAgreement = new SharingAgreementReferenceResponse(detail.getSharingAgreement().id(),
                detail.getSharingAgreement().name(), detail.getSharingAgreement().status());
    }

    public BigDecimal getCoefficient() {
        return coefficient;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public SharingAgreementReferenceResponse getSharingAgreement() {
        return sharingAgreement;
    }
}
