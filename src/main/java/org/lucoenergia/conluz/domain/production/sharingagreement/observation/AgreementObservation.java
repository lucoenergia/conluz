package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import java.util.List;
import java.util.Map;

/**
 * What was observed of one agreement, with its per-supply rows.
 *
 * @param earliestObservedDate     the earliest observed application date of its rows, or null
 * @param mostFrequentObservedDate the most frequent observed application date of its rows, or null
 * @param leavesDistributionStatusCounts its coefficient rows that leave the distribution by dropping to 0 (OBS-016),
 *                                       per status; they are among the coefficient rows, never the leaving rows
 * @param unallocatedShare         null when the agreement was never observed applied
 */
public record AgreementObservation(ObservedAgreement agreement, ObservedDate earliestObservedDate,
                                   ObservedDate mostFrequentObservedDate,
                                   Map<ObservationStatus, Integer> coefficientStatusCounts,
                                   Map<ObservationStatus, Integer> leavingStatusCounts,
                                   Map<ObservationStatus, Integer> leavesDistributionStatusCounts,
                                   List<SharingAnomaly> anomalies, Map<SharingAnomalyType, Integer> anomalyCounts,
                                   UnallocatedShare unallocatedShare, List<SharingRecommendation> recommendations,
                                   List<CoefficientObservation> coefficients, List<LeavingObservation> leavingRows) {
}
