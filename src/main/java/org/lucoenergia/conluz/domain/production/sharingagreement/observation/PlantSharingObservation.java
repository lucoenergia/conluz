package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The observation of a plant's PUBLISHED and SUPERSEDED agreements from its daily Datadis data (#579).
 *
 * @param consecutiveDays       N: the valid days a run or an anomaly must last
 * @param unattributedAnomalies the anomalies attributed to no agreement
 */
public record PlantSharingObservation(AnalysisStatus analysis, int consecutiveDays,
                                      List<AgreementObservation> agreements,
                                      List<SharingAnomaly> unattributedAnomalies) {

    /**
     * The agreement's slice of this observation (OBS-007), or empty when the agreement is not analysed.
     */
    public Optional<SharingAgreementObservation> sliceFor(UUID sharingAgreementId) {
        return agreements.stream()
                .filter(observation -> observation.agreement().id().equals(sharingAgreementId))
                .findFirst()
                .map(observation -> new SharingAgreementObservation(analysis, consecutiveDays, observation));
    }
}
