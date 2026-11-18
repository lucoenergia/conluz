package org.lucoenergia.conluz.infrastructure.production.sharingagreement.observation;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.PlantSharingObservation;

import java.util.List;

/**
 * How the distributor was observed to apply all the plant's PUBLISHED and SUPERSEDED agreements.
 */
@Schema(requiredProperties = {"analysis", "consecutiveDays", "sharingAgreements", "unattributedAnomalies"})
public class PlantSharingObservationResponse {

    @Schema(description = "What could be analysed")
    private final SharingObservationAnalysisResponse analysis;

    @Schema(description = "The consecutive valid days a run must last to be observed, and an anomaly to be " +
            "reported", example = "3")
    private final int consecutiveDays;

    @Schema(description = "The plant's PUBLISHED and SUPERSEDED agreements, in registered order")
    private final List<SharingAgreementObservationSummaryResponse> sharingAgreements;

    @Schema(description = "Anomalies attributed to no agreement: observed before anything was observed applied " +
            "for the supply")
    private final List<SharingAnomalyResponse> unattributedAnomalies;

    public PlantSharingObservationResponse(PlantSharingObservation observation) {
        this.analysis = new SharingObservationAnalysisResponse(observation.analysis());
        this.consecutiveDays = observation.consecutiveDays();
        this.sharingAgreements = observation.agreements().stream()
                .map(SharingAgreementObservationSummaryResponse::new)
                .toList();
        this.unattributedAnomalies = observation.unattributedAnomalies().stream()
                .map(SharingAnomalyResponse::new)
                .toList();
    }

    public SharingObservationAnalysisResponse getAnalysis() {
        return analysis;
    }

    public int getConsecutiveDays() {
        return consecutiveDays;
    }

    public List<SharingAgreementObservationSummaryResponse> getSharingAgreements() {
        return sharingAgreements;
    }

    public List<SharingAnomalyResponse> getUnattributedAnomalies() {
        return unattributedAnomalies;
    }
}
