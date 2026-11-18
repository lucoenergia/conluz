package org.lucoenergia.conluz.infrastructure.production.sharingagreement.observation;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.SharingAgreementObservation;

import java.util.List;

/**
 * One agreement's slice of the plant's observed sharing, with its per-supply rows.
 */
@Schema(requiredProperties = {"analysis", "consecutiveDays", "summary", "coefficients", "leavingRows"})
public class SharingAgreementObservationResponse {

    @Schema(description = "What could be analysed for the plant")
    private final SharingObservationAnalysisResponse analysis;

    @Schema(description = "The consecutive valid days a run must last to be observed, and an anomaly to be " +
            "reported", example = "3")
    private final int consecutiveDays;

    @Schema(description = "The agreement's summary, the same as its item in the plant's observed sharing")
    private final SharingAgreementObservationSummaryResponse summary;

    @Schema(description = "Its coefficient rows of the analysed supplies, ordered by supply code")
    private final List<CoefficientObservationResponse> coefficients;

    @Schema(description = "Supplies absent from it that had a non-zero value in the agreement observed before, " +
            "ordered by supply code")
    private final List<LeavingObservationResponse> leavingRows;

    public SharingAgreementObservationResponse(SharingAgreementObservation observation) {
        this.analysis = new SharingObservationAnalysisResponse(observation.analysis());
        this.consecutiveDays = observation.consecutiveDays();
        this.summary = new SharingAgreementObservationSummaryResponse(observation.agreement());
        this.coefficients = observation.agreement().coefficients().stream()
                .map(CoefficientObservationResponse::new)
                .toList();
        this.leavingRows = observation.agreement().leavingRows().stream()
                .map(LeavingObservationResponse::new)
                .toList();
    }

    public SharingObservationAnalysisResponse getAnalysis() {
        return analysis;
    }

    public int getConsecutiveDays() {
        return consecutiveDays;
    }

    public SharingAgreementObservationSummaryResponse getSummary() {
        return summary;
    }

    public List<CoefficientObservationResponse> getCoefficients() {
        return coefficients;
    }

    public List<LeavingObservationResponse> getLeavingRows() {
        return leavingRows;
    }
}
