package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What walking one supply's valid days found: its observations, and how each day was classified.
 *
 * @param days              the supply's valid days, in order
 * @param observations      every observation, in order
 * @param firstObservations each candidate's first observation
 * @param anomalousTypes    per day, the anomaly type the day shows, or null
 * @param currentBefore     per day, the candidate observed applied before the day, or null
 * @param matchedCandidates per day, the candidate the day is read as, or null when it matches none
 */
record SupplyTimeline(List<ValidDay> days, List<Observation> observations,
                      Map<Candidate, Observation> firstObservations, SharingAnomalyType[] anomalousTypes,
                      Candidate[] currentBefore, Candidate[] matchedCandidates) {

    Optional<Observation> firstObservation(Candidate candidate) {
        return Optional.ofNullable(firstObservations.get(candidate));
    }

    /**
     * The observation that followed the given one, if any.
     */
    Optional<Observation> next(Observation observation) {
        int index = observations.indexOf(observation);
        return index + 1 < observations.size() ? Optional.of(observations.get(index + 1)) : Optional.empty();
    }

    /**
     * A run of N days matching a candidate.
     *
     * @param previous       the candidate observed applied before, or null
     * @param runStartIndex  the index of the run's first day
     * @param first          whether it is the candidate's first observation
     * @param reverted       whether it re-applies a candidate a later-started one had replaced
     */
    record Observation(Candidate candidate, Candidate previous, ObservedDate date, int runStartIndex,
                       boolean first, boolean reverted) {
    }
}
