package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import org.lucoenergia.conluz.domain.production.sharingagreement.RegisteredOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the status of each of a supply's coefficient rows, and of its leaving rows (OBS-002, OBS-003,
 * OBS-008), from its timeline; the plant's own supply's rows are not observable (OBS-011). Their observed β rests on the agreement's applied window, which only all the
 * supplies' rows together decide: {@link AgreementObservationSummarizer} sets it.
 */
final class ObservationStatusResolver {

    private final ZoneId zone;

    ObservationStatusResolver(ZoneId zone) {
        this.zone = zone;
    }

    /**
     * @param agreements the agreements in scope, in registered order: an agreement's rank is its index
     * @param candidates the supply's candidates, one per agreement, built from the same order: a candidate's rank is
     *                   its index
     */
    SupplyRows resolve(ObservedSupply supply, SupplyTimeline timeline, List<ObservedAgreement> agreements,
                       List<Candidate> candidates) {
        Map<UUID, CoefficientObservation> coefficients = new LinkedHashMap<>();
        Map<UUID, LeavingObservation> leavings = new HashMap<>();
        for (Candidate candidate : candidates) {
            ObservedAgreement agreement = agreements.get(candidate.rank());
            if (candidate.isImplicitZero()) {
                leavingRow(supply, timeline, agreement, candidate, agreements)
                        .ifPresent(row -> leavings.put(agreement.id(), row));
            } else {
                coefficients.put(agreement.id(), coefficientRow(supply, timeline, agreement, candidate));
            }
        }
        return new SupplyRows(coefficients, leavings);
    }

    /**
     * The rows of the plant's own supply (OBS-011): never observed, so no timeline, no date, no observed β and no
     * leaving row.
     *
     * @param candidates the supply's candidates, built from the same order as {@code agreements}
     */
    SupplyRows notObservable(ObservedSupply supply, List<ObservedAgreement> agreements, List<Candidate> candidates) {
        Map<UUID, CoefficientObservation> coefficients = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            if (candidate.isImplicitZero()) {
                continue;
            }
            ObservedCoefficient row = candidate.row();
            LocalDate storedValidFrom = RegisteredOrder.localDay(row.validFrom(), zone);
            LocalDate storedValidTo = RegisteredOrder.localDay(row.validTo(), zone);
            coefficients.put(agreements.get(candidate.rank()).id(), new CoefficientObservation(row.id(), supply,
                    row.coefficient(), storedValidFrom, storedValidTo, ObservationStatus.NOT_OBSERVABLE, null, null,
                    ObservedBeta.NONE, 0, null, null));
        }
        return new SupplyRows(coefficients, Map.of());
    }

    private CoefficientObservation coefficientRow(ObservedSupply supply, SupplyTimeline timeline,
                                                  ObservedAgreement agreement, Candidate candidate) {
        ObservedCoefficient row = candidate.row();
        LocalDate storedValidFrom = RegisteredOrder.localDay(row.validFrom(), zone);
        LocalDate storedValidTo = RegisteredOrder.localDay(row.validTo(), zone);
        if (timeline.days().isEmpty()) {
            return new CoefficientObservation(row.id(), supply, row.coefficient(), storedValidFrom, storedValidTo,
                    ObservationStatus.NO_DATA, null, null, null, 0, null, null);
        }
        Optional<SupplyTimeline.Observation> observation = timeline.firstObservation(candidate);
        if (observation.isPresent()) {
            ObservedDate date = observation.get().date();
            ObservationStatus status = date.admits(storedValidFrom)
                    ? ObservationStatus.APPLIED_SAME_DATE : ObservationStatus.APPLIED_DIFFERENT_DATE;
            return new CoefficientObservation(row.id(), supply, row.coefficient(), storedValidFrom, storedValidTo,
                    status, date, null, null, 0, null, null);
        }
        ObservationStatus status = lastObservedBefore(timeline, candidate.rank())
                .filter(previous -> Candidate.sameValue(previous.value(), candidate.value()))
                .map(previous -> ObservationStatus.NOT_DISTINGUISHABLE)
                .orElseGet(() -> notObserved(agreement));
        return new CoefficientObservation(row.id(), supply, row.coefficient(), storedValidFrom, storedValidTo, status,
                null, null, null, 0, null, null);
    }

    /**
     * The leaving row of an agreement the supply is absent from, when the agreement observed before it gave the
     * supply a non-zero value.
     */
    private Optional<LeavingObservation> leavingRow(ObservedSupply supply, SupplyTimeline timeline,
                                                    ObservedAgreement agreement, Candidate implicitZero,
                                                    List<ObservedAgreement> agreements) {
        Optional<SupplyTimeline.Observation> observation = timeline.firstObservation(implicitZero);
        if (observation.isPresent()) {
            // An implicit 0 is only observed after a non-zero value, so the previous candidate is a row.
            ObservedCoefficient previous = observation.get().previous().row();
            ObservedDate date = observation.get().date();
            LocalDate storedValidTo = RegisteredOrder.localDay(previous.validTo(), zone);
            ObservationStatus status = date.admits(storedValidTo)
                    ? ObservationStatus.APPLIED_SAME_DATE : ObservationStatus.APPLIED_DIFFERENT_DATE;
            return Optional.of(new LeavingObservation(supply, agreements.get(observation.get().previous().rank()),
                    previous.id(), previous.coefficient(), date, storedValidTo, status, null, null, 0, null, null));
        }
        return lastObservedBefore(timeline, implicitZero.rank())
                .filter(previous -> !previous.isImplicitZero()
                        && previous.row().coefficient().compareTo(BigDecimal.ZERO) > 0)
                .map(previous -> {
                    ObservationStatus status = Candidate.sameValue(previous.value(), 0)
                            ? ObservationStatus.NOT_DISTINGUISHABLE : notObserved(agreement);
                    return new LeavingObservation(supply, agreements.get(previous.rank()), previous.row().id(),
                            previous.row().coefficient(), null,
                            RegisteredOrder.localDay(previous.row().validTo(), zone), status, null, null, 0, null,
                            null);
                });
    }

    /**
     * The candidate observed for the supply in the latest agreement before the given rank in registered order. A
     * supply has one candidate per agreement, so one per rank.
     */
    private static Optional<Candidate> lastObservedBefore(SupplyTimeline timeline, int rank) {
        return timeline.firstObservations().keySet().stream()
                .filter(candidate -> candidate.rank() < rank)
                .max(Comparator.comparingInt(Candidate::rank));
    }

    private static ObservationStatus notObserved(ObservedAgreement agreement) {
        return agreement.distributorFileSentOn() != null
                ? ObservationStatus.PENDING_APPLICATION : ObservationStatus.NOT_OBSERVED;
    }
}
