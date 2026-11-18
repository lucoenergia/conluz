package org.lucoenergia.conluz.infrastructure.production.sharingagreement.observation;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.CoefficientObservation;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.ObservationStatus;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.SupplyReferenceResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What was observed of one coefficient row of the agreement.
 */
@Schema(requiredProperties = {"coefficientId", "supply", "coefficient", "storedValidFrom", "status", "observedDate",
        "proposedDate"})
public class CoefficientObservationResponse {

    @Schema(description = "Identifier of the coefficient row", example = "b3d1a2f0-1234-5678-abcd-000000000001")
    private final UUID coefficientId;

    @Schema(description = "Supply the coefficient belongs to")
    private final SupplyReferenceResponse supply;

    @Schema(description = "Registered coefficient, on a 0-1 scale", example = "0.030763")
    private final BigDecimal coefficient;

    @Schema(description = "Local day the coefficient is stored to start applying. Null when pending.",
            example = "2024-05-10", types = {"string", "null"})
    private final LocalDate storedValidFrom;

    @Schema(description = "How its observed application compares with what is stored", example = "APPLIED_SAME_DATE")
    private final ObservationStatus status;

    @Schema(description = "When the distributor was observed to start applying it. Null when not observed.",
            types = {"object", "null"})
    private final ObservedDateResponse observedDate;

    @Schema(description = "For NOT_DISTINGUISHABLE, the agreement's most frequent observed application date. " +
            "Null otherwise, or when nothing of the agreement was observed.", types = {"object", "null"})
    private final ObservedDateResponse proposedDate;

    public CoefficientObservationResponse(CoefficientObservation row) {
        this.coefficientId = row.coefficientId();
        this.supply = new SupplyReferenceResponse(row.supply().id(), row.supply().code(), row.supply().name());
        this.coefficient = row.coefficient();
        this.storedValidFrom = row.storedValidFrom();
        this.status = row.status();
        this.observedDate = ObservedDateResponse.of(row.observedDate());
        this.proposedDate = ObservedDateResponse.of(row.proposedDate());
    }

    public UUID getCoefficientId() {
        return coefficientId;
    }

    public SupplyReferenceResponse getSupply() {
        return supply;
    }

    public BigDecimal getCoefficient() {
        return coefficient;
    }

    public LocalDate getStoredValidFrom() {
        return storedValidFrom;
    }

    public ObservationStatus getStatus() {
        return status;
    }

    public ObservedDateResponse getObservedDate() {
        return observedDate;
    }

    public ObservedDateResponse getProposedDate() {
        return proposedDate;
    }
}
