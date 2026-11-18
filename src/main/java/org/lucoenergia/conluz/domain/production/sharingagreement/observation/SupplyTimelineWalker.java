package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Walks one supply's valid days in order and finds the runs of N days that show a candidate being applied
 * (OBS-001, OBS-004, OBS-008).
 *
 * <p>The walker keeps the candidate last observed applied, {@code current}. A day matching it is consistent.
 * On any other day, every candidate the day matches extends its run, provided it can be told apart from
 * {@code current}; a run reaching N days is an observation. A day matching both {@code current} and another
 * candidate of a different value is ambiguous for that candidate: it neither extends nor breaks its run, and
 * counts as a gap before it. An implicit 0 is only observed as a leaving: after a candidate with a non-zero
 * value.
 */
final class SupplyTimelineWalker {

    private final int consecutiveDays;

    SupplyTimelineWalker(int consecutiveDays) {
        this.consecutiveDays = consecutiveDays;
    }

    SupplyTimeline walk(List<ValidDay> days, List<Candidate> candidates) {
        boolean inSomeAgreement = candidates.stream().anyMatch(candidate -> !Candidate.sameValue(candidate.value(), 0));

        SharingAnomalyType[] anomalousTypes = new SharingAnomalyType[days.size()];
        Candidate[] currentBefore = new Candidate[days.size()];
        List<SupplyTimeline.Observation> observations = new ArrayList<>();
        Map<Candidate, SupplyTimeline.Observation> firstObservations = new LinkedHashMap<>();

        Map<Candidate, Integer> runLength = new HashMap<>();
        Map<Candidate, Integer> runStart = new HashMap<>();
        // The last valid day that was not ambiguous for each candidate, and its value when the candidate's run
        // started: what tells an exact date from a range.
        Map<Candidate, LocalDate> lastClearDay = new HashMap<>();
        Map<Candidate, LocalDate> clearDayBeforeRun = new HashMap<>();
        Candidate current = null;

        for (int i = 0; i < days.size(); i++) {
            ValidDay day = days.get(i);
            currentBefore[i] = current;
            Set<Candidate> matching = candidates.stream()
                    .filter(candidate -> candidate.matches(day.beta()))
                    .collect(Collectors.toSet());

            if (current != null && matching.contains(current)) {
                for (Candidate candidate : candidates) {
                    if (candidate.equals(current)
                            || !matching.contains(candidate)
                            || Candidate.sameValue(candidate.value(), current.value())) {
                        if (!candidate.equals(current)) {
                            runLength.remove(candidate);
                        }
                        lastClearDay.put(candidate, day.day());
                    }
                    // Otherwise the day is ambiguous for the candidate: its run and its last clear day are kept.
                }
                continue;
            }

            for (Candidate candidate : candidates) {
                if (matching.contains(candidate) && isObservable(candidate, current)) {
                    if (runLength.getOrDefault(candidate, 0) == 0) {
                        runStart.put(candidate, i);
                        clearDayBeforeRun.put(candidate, lastClearDay.get(candidate));
                    }
                    runLength.merge(candidate, 1, Integer::sum);
                } else {
                    runLength.remove(candidate);
                }
                lastClearDay.put(candidate, day.day());
            }
            anomalousTypes[i] = matching.isEmpty() ? anomalyOf(day, current, inSomeAgreement) : null;

            Candidate observed = completedRun(candidates, runLength, firstObservations);
            if (observed != null) {
                int start = runStart.get(observed);
                ObservedDate date = dateOf(days.get(start).day(), clearDayBeforeRun.get(observed));
                SupplyTimeline.Observation first = firstObservations.get(observed);
                boolean reverted = first != null && current != null
                        && firstObservations.get(current).date().to().isAfter(first.date().to());
                SupplyTimeline.Observation observation =
                        new SupplyTimeline.Observation(observed, current, date, start, first == null, reverted);
                observations.add(observation);
                firstObservations.putIfAbsent(observed, observation);
                current = observed;
                runLength.clear();
            }
        }
        return new SupplyTimeline(days, observations, firstObservations, anomalousTypes, currentBefore);
    }

    /**
     * Whether a run of the candidate can be observed after {@code current}: its value differs from the current
     * one, and an implicit 0 only follows a non-zero value.
     */
    private static boolean isObservable(Candidate candidate, Candidate current) {
        if (current == null) {
            return !candidate.isImplicitZero();
        }
        return !Candidate.sameValue(candidate.value(), current.value());
    }

    /**
     * The anomaly a day matching no candidate shows. Before the supply's first observation it is not yet in
     * the distribution, so a day without energy is no anomaly.
     */
    private static SharingAnomalyType anomalyOf(ValidDay day, Candidate current, boolean inSomeAgreement) {
        if (Candidate.sameValue(day.beta(), 0)) {
            return current == null ? null : SharingAnomalyType.NO_ENERGY_IN_AGREEMENT;
        }
        return inSomeAgreement ? SharingAnomalyType.UNKNOWN_COEFFICIENT : SharingAnomalyType.ENERGY_OUTSIDE_AGREEMENT;
    }

    /**
     * The candidate whose run has reached N days. Ties go first to a candidate not yet observed, then to the
     * earliest in registered order.
     */
    private Candidate completedRun(List<Candidate> candidates, Map<Candidate, Integer> runLength,
                                   Map<Candidate, SupplyTimeline.Observation> firstObservations) {
        return candidates.stream()
                .filter(candidate -> runLength.getOrDefault(candidate, 0) >= consecutiveDays)
                .min(Comparator.<Candidate, Boolean>comparing(firstObservations::containsKey)
                        .thenComparingInt(Candidate::rank)
                        .thenComparing(candidate -> candidate.isImplicitZero() ? "" : candidate.row().id().toString()))
                .orElse(null);
    }

    /**
     * The date of a run starting on {@code runStart}: exact when the day before it was clear, a range over the
     * gap otherwise, and open when no clear day precedes it.
     */
    private static ObservedDate dateOf(LocalDate runStart, LocalDate clearDayBefore) {
        if (clearDayBefore == null) {
            return ObservedDate.onOrBefore(runStart);
        }
        return new ObservedDate(clearDayBefore.plusDays(1), runStart);
    }
}
