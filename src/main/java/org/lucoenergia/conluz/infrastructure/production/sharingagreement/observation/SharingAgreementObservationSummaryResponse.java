package org.lucoenergia.conluz.infrastructure.production.sharingagreement.observation;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.AgreementObservation;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.SharingRecommendation;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.SharingAgreementReferenceResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * What was observed of one agreement, without its per-supply rows.
 */
@Schema(requiredProperties = {"sharingAgreement", "distributorFileSentOn", "earliestObservedDate",
        "mostFrequentObservedDate", "coefficientStatusCounts", "leavingStatusCounts", "anomalies", "anomalyCounts",
        "unallocatedShare", "recommendations"})
public class SharingAgreementObservationSummaryResponse {

    @Schema(description = "The agreement")
    private final SharingAgreementReferenceResponse sharingAgreement;

    @Schema(description = "Local day the agreement's distributor file was sent. Null when not recorded.",
            example = "2024-04-25", types = {"string", "null"})
    private final LocalDate distributorFileSentOn;

    @Schema(description = "Earliest observed application date of its coefficients. Null when none was observed.",
            types = {"object", "null"})
    private final ObservedDateResponse earliestObservedDate;

    @Schema(description = "Most frequent observed application date of its coefficients. Null when none was " +
            "observed.", types = {"object", "null"})
    private final ObservedDateResponse mostFrequentObservedDate;

    @Schema(description = "Number of its coefficient rows in each status")
    private final ObservationStatusCountsResponse coefficientStatusCounts;

    @Schema(description = "Number of its leaving rows in each status")
    private final ObservationStatusCountsResponse leavingStatusCounts;

    @Schema(description = "Anomalies attributed to the agreement")
    private final List<SharingAnomalyResponse> anomalies;

    @Schema(description = "Number of anomalies attributed to the agreement, per type")
    private final SharingAnomalyCountsResponse anomalyCounts;

    @Schema(description = "The production no supply with data received while the agreement was applied. Null " +
            "when it was never observed applied, or no valid day falls while it was.", types = {"object", "null"})
    private final UnallocatedShareResponse unallocatedShare;

    @Schema(description = "What to do with the agreement, in this order: SET_DATES, DEACTIVATE, REVIEW_ANOMALIES; " +
            "NONE when none applies. Empty when the plant is not computable. Never a coefficient value.")
    private final List<SharingRecommendation> recommendations;

    public SharingAgreementObservationSummaryResponse(AgreementObservation observation) {
        this.sharingAgreement = new SharingAgreementReferenceResponse(observation.agreement().id(),
                observation.agreement().name(), observation.agreement().status());
        this.distributorFileSentOn = observation.agreement().distributorFileSentOn();
        this.earliestObservedDate = ObservedDateResponse.of(observation.earliestObservedDate());
        this.mostFrequentObservedDate = ObservedDateResponse.of(observation.mostFrequentObservedDate());
        this.coefficientStatusCounts = new ObservationStatusCountsResponse(observation.coefficientStatusCounts());
        this.leavingStatusCounts = new ObservationStatusCountsResponse(observation.leavingStatusCounts());
        this.anomalies = observation.anomalies().stream().map(SharingAnomalyResponse::new).toList();
        this.anomalyCounts = new SharingAnomalyCountsResponse(observation.anomalyCounts());
        this.unallocatedShare = observation.unallocatedShare() == null
                ? null : new UnallocatedShareResponse(observation.unallocatedShare());
        this.recommendations = observation.recommendations();
    }

    public SharingAgreementReferenceResponse getSharingAgreement() {
        return sharingAgreement;
    }

    public LocalDate getDistributorFileSentOn() {
        return distributorFileSentOn;
    }

    public ObservedDateResponse getEarliestObservedDate() {
        return earliestObservedDate;
    }

    public ObservedDateResponse getMostFrequentObservedDate() {
        return mostFrequentObservedDate;
    }

    public ObservationStatusCountsResponse getCoefficientStatusCounts() {
        return coefficientStatusCounts;
    }

    public ObservationStatusCountsResponse getLeavingStatusCounts() {
        return leavingStatusCounts;
    }

    public List<SharingAnomalyResponse> getAnomalies() {
        return anomalies;
    }

    public SharingAnomalyCountsResponse getAnomalyCounts() {
        return anomalyCounts;
    }

    public UnallocatedShareResponse getUnallocatedShare() {
        return unallocatedShare;
    }

    public List<SharingRecommendation> getRecommendations() {
        return recommendations;
    }
}
