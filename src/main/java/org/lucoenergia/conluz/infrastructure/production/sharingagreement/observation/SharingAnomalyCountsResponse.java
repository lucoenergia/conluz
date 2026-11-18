package org.lucoenergia.conluz.infrastructure.production.sharingagreement.observation;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.SharingAnomalyType;

import java.util.Map;

/**
 * The number of anomalies of each type.
 */
@Schema(requiredProperties = {"unknownCoefficient", "energyOutsideAgreement", "noEnergyInAgreement", "reverted",
        "appliedBeforeSent"})
public class SharingAnomalyCountsResponse {

    @Schema(description = "Anomalies of type UNKNOWN_COEFFICIENT", example = "1")
    private final int unknownCoefficient;

    @Schema(description = "Anomalies of type ENERGY_OUTSIDE_AGREEMENT", example = "0")
    private final int energyOutsideAgreement;

    @Schema(description = "Anomalies of type NO_ENERGY_IN_AGREEMENT", example = "0")
    private final int noEnergyInAgreement;

    @Schema(description = "Anomalies of type REVERTED", example = "0")
    private final int reverted;

    @Schema(description = "Anomalies of type APPLIED_BEFORE_SENT", example = "2")
    private final int appliedBeforeSent;

    public SharingAnomalyCountsResponse(Map<SharingAnomalyType, Integer> counts) {
        this.unknownCoefficient = counts.getOrDefault(SharingAnomalyType.UNKNOWN_COEFFICIENT, 0);
        this.energyOutsideAgreement = counts.getOrDefault(SharingAnomalyType.ENERGY_OUTSIDE_AGREEMENT, 0);
        this.noEnergyInAgreement = counts.getOrDefault(SharingAnomalyType.NO_ENERGY_IN_AGREEMENT, 0);
        this.reverted = counts.getOrDefault(SharingAnomalyType.REVERTED, 0);
        this.appliedBeforeSent = counts.getOrDefault(SharingAnomalyType.APPLIED_BEFORE_SENT, 0);
    }

    public int getUnknownCoefficient() {
        return unknownCoefficient;
    }

    public int getEnergyOutsideAgreement() {
        return energyOutsideAgreement;
    }

    public int getNoEnergyInAgreement() {
        return noEnergyInAgreement;
    }

    public int getReverted() {
        return reverted;
    }

    public int getAppliedBeforeSent() {
        return appliedBeforeSent;
    }
}
