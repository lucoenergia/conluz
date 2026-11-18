package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

/**
 * One agreement's slice of a {@link PlantSharingObservation}.
 */
public record SharingAgreementObservation(AnalysisStatus analysis, int consecutiveDays,
                                          AgreementObservation agreement) {
}
