package org.lucoenergia.conluz.infrastructure.production.sharingagreement.get;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientApplicationState;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientEndState;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.SharingAgreementCoefficient;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.SupplyReferenceResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(requiredProperties = {"coefficientId", "supply", "coefficient", "validFrom", "validTo",
        "applicationState", "endState", "endDate", "currentCoefficient"})
public class SharingAgreementPartitionCoefficientResponse {

    @Schema(description = "Internal unique identifier of this coefficient", example = "b3d1a2f0-1234-5678-abcd-000000000001")
    private final UUID coefficientId;

    @Schema(description = "Supply this coefficient belongs to")
    private final SupplyReferenceResponse supply;

    @Schema(description = "Partition coefficient value, on a 0-1 scale", example = "0.030763")
    private final BigDecimal coefficient;

    @Schema(description = "Start of the period during which this coefficient is active (inclusive). " +
            "Null means this is a pending coefficient, materialised but not yet activated.",
            example = "2024-05-23T00:00:00Z", types = {"string", "null"})
    private final Instant validFrom;

    @Schema(description = "End of the period (exclusive), as stored. Null unless explicitly closed.",
            example = "2025-01-01T00:00:00Z", types = {"string", "null"})
    private final Instant validTo;

    @Schema(description = "Whether the distributor has applied this coefficient yet")
    private final CoefficientApplicationState applicationState;

    @Schema(description = "How/why this coefficient's coverage ends")
    private final CoefficientEndState endState;

    @Schema(description = "The effective end of this coefficient's coverage. Present only when " +
            "endState is DERIVED or CLOSED.", example = "2025-01-01T00:00:00Z", types = {"string", "null"})
    private final Instant endDate;

    @Schema(description = "The coefficient this supply is currently on in this agreement's plant -- " +
            "what the value in this row would replace. Null when the supply has no active " +
            "coefficient in this plant.", types = {"object", "null"})
    private final CurrentCoefficientResponse currentCoefficient;

    public SharingAgreementPartitionCoefficientResponse(SharingAgreementCoefficient view) {
        this.coefficientId = view.getCoefficientId();
        this.supply = new SupplyReferenceResponse(view.getSupplyId(), view.getSupplyCode(), view.getSupplyName());
        this.coefficient = view.getCoefficient();
        this.validFrom = view.getValidFrom();
        this.validTo = view.getValidTo();
        this.applicationState = view.getApplicationState();
        this.endState = view.getEndState();
        this.endDate = view.getEndDate();
        this.currentCoefficient = view.getCurrentCoefficient() == null
                ? null : new CurrentCoefficientResponse(view.getCurrentCoefficient());
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

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public CoefficientApplicationState getApplicationState() {
        return applicationState;
    }

    public CoefficientEndState getEndState() {
        return endState;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public CurrentCoefficientResponse getCurrentCoefficient() {
        return currentCoefficient;
    }
}
