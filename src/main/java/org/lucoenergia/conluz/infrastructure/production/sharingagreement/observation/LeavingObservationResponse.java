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
        "observedLeavingDate", "storedValidTo", "status", "proposedDate", "observedBeta", "observedValidDays",
        "windowDays", "windowFrom", "windowTo"})
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

    @Schema(description = "Median of the supply's daily observed β (E / G) over its valid days from the row's " +
            "observed leaving date (the latest day of a range or of an open date) to the end of the agreement's " +
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

    @Schema(description = "Number of plant-valid days in the row's window: from its observed leaving date (the " +
            "latest day of a range or of an open date), or from the start of the agreement's applied window when " +
            "it has none, to the end of the agreement's applied window. The days observedValidDays is counted " +
            "over, so never less than it. 0 when the agreement has no applied window or the row's start falls " +
            "after its end.", example = "335")
    private final int windowDays;

    @Schema(description = "First local day of the row's window: its observed leaving date (the latest day of a " +
            "range or of an open date), or the start of the agreement's applied window when it has none. Null when " +
            "the row has no window (windowDays 0): the agreement has no applied window or the row's start falls " +
            "after its end.", example = "2024-02-01", types = {"string", "null"})
    private final LocalDate windowFrom;

    @Schema(description = "Last local day of the row's window: the end of the agreement's applied window. Null when " +
            "the row has no window (windowDays 0).", example = "2025-11-30", types = {"string", "null"})
    private final LocalDate windowTo;

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
        this.observedBeta = row.observedBeta().value();
        this.observedValidDays = row.observedBeta().validDays();
        this.windowDays = row.windowDays();
        this.windowFrom = row.windowFrom();
        this.windowTo = row.windowTo();
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

    public BigDecimal getObservedBeta() {
        return observedBeta;
    }

    public int getObservedValidDays() {
        return observedValidDays;
    }

    public int getWindowDays() {
        return windowDays;
    }

    public LocalDate getWindowFrom() {
        return windowFrom;
    }

    public LocalDate getWindowTo() {
        return windowTo;
    }
}
