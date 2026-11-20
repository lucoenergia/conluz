package org.lucoenergia.conluz.infrastructure.production.sharingagreement.observation;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.ObservationStatus;

import java.util.Map;

/**
 * The number of rows in each observation status.
 */
@Schema(requiredProperties = {"appliedSameDate", "appliedDifferentDate", "pendingApplication", "notDistinguishable",
        "notObserved", "noData", "notObservable"})
public class ObservationStatusCountsResponse {

    @Schema(description = "Rows observed applied on their stored date", example = "12")
    private final int appliedSameDate;

    @Schema(description = "Rows observed applied on another date than the stored one, or without one", example = "3")
    private final int appliedDifferentDate;

    @Schema(description = "Rows not observed, of an agreement whose distributor file was sent", example = "0")
    private final int pendingApplication;

    @Schema(description = "Rows whose value equals the supply's value in the agreement observed before, so their " +
            "application cannot be observed", example = "5")
    private final int notDistinguishable;

    @Schema(description = "Rows not observed, of an agreement whose distributor file was not sent", example = "0")
    private final int notObserved;

    @Schema(description = "Rows of supplies without any valid day", example = "1")
    private final int noData;

    @Schema(description = "Rows of the plant's own supply, whose share cannot be observed from Datadis", example = "1")
    private final int notObservable;

    public ObservationStatusCountsResponse(Map<ObservationStatus, Integer> counts) {
        this.appliedSameDate = counts.getOrDefault(ObservationStatus.APPLIED_SAME_DATE, 0);
        this.appliedDifferentDate = counts.getOrDefault(ObservationStatus.APPLIED_DIFFERENT_DATE, 0);
        this.pendingApplication = counts.getOrDefault(ObservationStatus.PENDING_APPLICATION, 0);
        this.notDistinguishable = counts.getOrDefault(ObservationStatus.NOT_DISTINGUISHABLE, 0);
        this.notObserved = counts.getOrDefault(ObservationStatus.NOT_OBSERVED, 0);
        this.noData = counts.getOrDefault(ObservationStatus.NO_DATA, 0);
        this.notObservable = counts.getOrDefault(ObservationStatus.NOT_OBSERVABLE, 0);
    }

    public int getAppliedSameDate() {
        return appliedSameDate;
    }

    public int getAppliedDifferentDate() {
        return appliedDifferentDate;
    }

    public int getPendingApplication() {
        return pendingApplication;
    }

    public int getNotDistinguishable() {
        return notDistinguishable;
    }

    public int getNotObserved() {
        return notObserved;
    }

    public int getNoData() {
        return noData;
    }

    public int getNotObservable() {
        return notObservable;
    }
}
