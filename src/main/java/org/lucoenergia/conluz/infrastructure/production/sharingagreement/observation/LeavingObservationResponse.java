package org.lucoenergia.conluz.infrastructure.production.sharingagreement.observation;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.LeavingObservation;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.ObservationStatus;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.SharingAgreementReferenceResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.SupplyReferenceResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A supply leaving the distribution in this agreement, which it is absent from, after the previous agreement
 * was observed applied for it.
 */
@Schema(requiredProperties = {"supply", "previousSharingAgreement", "previousCoefficientId", "previousCoefficient",
        "observedLeavingDate", "storedValidTo", "status", "proposedDate"})
public class LeavingObservationResponse {

    @Schema(description = "Supply leaving the distribution")
    private final SupplyReferenceResponse supply;

    @Schema(description = "Agreement observed applied for the supply before this one")
    private final SharingAgreementReferenceResponse previousSharingAgreement;

    @Schema(description = "Identifier of the supply's coefficient row in the previous agreement",
            example = "b3d1a2f0-1234-5678-abcd-000000000001")
    private final UUID previousCoefficientId;

    @Schema(description = "The supply's coefficient in the previous agreement, on a 0-1 scale", example = "0.05")
    private final BigDecimal previousCoefficient;

    @Schema(description = "When the distributor was observed to stop giving the supply production. Null when not " +
            "observed.", types = {"object", "null"})
    private final ObservedDateResponse observedLeavingDate;

    @Schema(description = "Local day the previous coefficient is stored to stop applying (exclusive end). Null " +
            "when its period is open.", example = "2025-04-01", types = {"string", "null"})
    private final LocalDate storedValidTo;

    @Schema(description = "How the observed leaving compares with the stored end of the previous coefficient",
            example = "APPLIED_DIFFERENT_DATE")
    private final ObservationStatus status;

    @Schema(description = "For NOT_DISTINGUISHABLE, the agreement's most frequent observed application date. " +
            "Null otherwise, or when nothing of the agreement was observed.", types = {"object", "null"})
    private final ObservedDateResponse proposedDate;

    public LeavingObservationResponse(LeavingObservation row) {
        this.supply = new SupplyReferenceResponse(row.supply().id(), row.supply().code(), row.supply().name());
        this.previousSharingAgreement = new SharingAgreementReferenceResponse(row.previousAgreement().id(),
                row.previousAgreement().name(), row.previousAgreement().status());
        this.previousCoefficientId = row.previousCoefficientId();
        this.previousCoefficient = row.previousCoefficient();
        this.observedLeavingDate = ObservedDateResponse.of(row.observedLeavingDate());
        this.storedValidTo = row.storedValidTo();
        this.status = row.status();
        this.proposedDate = ObservedDateResponse.of(row.proposedDate());
    }

    public SupplyReferenceResponse getSupply() {
        return supply;
    }

    public SharingAgreementReferenceResponse getPreviousSharingAgreement() {
        return previousSharingAgreement;
    }

    public UUID getPreviousCoefficientId() {
        return previousCoefficientId;
    }

    public BigDecimal getPreviousCoefficient() {
        return previousCoefficient;
    }

    public ObservedDateResponse getObservedLeavingDate() {
        return observedLeavingDate;
    }

    public LocalDate getStoredValidTo() {
        return storedValidTo;
    }

    public ObservationStatus getStatus() {
        return status;
    }

    public ObservedDateResponse getProposedDate() {
        return proposedDate;
    }
}
