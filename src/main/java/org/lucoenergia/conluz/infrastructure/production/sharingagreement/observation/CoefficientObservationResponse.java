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
@Schema(requiredProperties = {"coefficientId", "supply", "coefficient", "storedValidFrom", "storedValidTo", "status",
        "observedDate", "proposedDate", "observedBeta", "observedValidDays"})
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

    @Schema(description = "Local day the coefficient is stored to stop applying (exclusive end). Null when its " +
            "period is open.", example = "2025-04-01", types = {"string", "null"})
    private final LocalDate storedValidTo;

    @Schema(description = "How its observed application compares with what is stored. NOT_OBSERVABLE for a " +
            "coefficient of the plant's own supply, whose share cannot be observed from Datadis",
            example = "APPLIED_SAME_DATE")
    private final ObservationStatus status;

    @Schema(description = "When the distributor was observed to start applying it. Null when not observed.",
            types = {"object", "null"})
    private final ObservedDateResponse observedDate;

    @Schema(description = "For NOT_DISTINGUISHABLE, the agreement's most frequent observed application date. " +
            "Null otherwise, or when nothing of the agreement was observed.", types = {"object", "null"})
    private final ObservedDateResponse proposedDate;

    @Schema(description = "Median of the supply's daily observed β (E / G) over its valid days from the row's " +
            "observed application date (the latest day of a range or of an open date) to the end of the agreement's " +
            "applied window, or over the whole applied window when the row has no observed date. The applied " +
            "window runs from the agreement's most frequent observed application date to the day before the next " +
            "agreement's, or to the end of the analysed range. On a 0-1 scale, rounded half-up to 6 decimals; for " +
            "an even number of days, the mean of the two middle values. Null when the agreement has no applied " +
            "window, the row's start falls after its end, or the supply has no valid day in between.",
            example = "0.030012",
            types = {"number", "null"})
    private final BigDecimal observedBeta;

    @Schema(description = "Number of valid days the observed β rests on. 0 when it is null.", example = "184")
    private final int observedValidDays;

    public CoefficientObservationResponse(CoefficientObservation row) {
        this.coefficientId = row.coefficientId();
        this.supply = new SupplyReferenceResponse(row.supply().id(), row.supply().code(), row.supply().name());
        this.coefficient = row.coefficient();
        this.storedValidFrom = row.storedValidFrom();
        this.storedValidTo = row.storedValidTo();
        this.status = row.status();
        this.observedDate = ObservedDateResponse.of(row.observedDate());
        this.proposedDate = ObservedDateResponse.of(row.proposedDate());
        this.observedBeta = row.observedBeta().value();
        this.observedValidDays = row.observedBeta().validDays();
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

    public LocalDate getStoredValidTo() {
        return storedValidTo;
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

    public BigDecimal getObservedBeta() {
        return observedBeta;
    }

    public int getObservedValidDays() {
        return observedValidDays;
    }
}
