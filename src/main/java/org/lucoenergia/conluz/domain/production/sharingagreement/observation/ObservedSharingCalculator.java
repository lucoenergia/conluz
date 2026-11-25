package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import org.lucoenergia.conluz.domain.production.sharingagreement.RegisteredOrder;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Observes how the distributor applies a plant's sharing agreements, from the daily energy each consumer
 * supply received and the plant produced (#579).
 *
 * <p>On each valid local day, a supply's observed β = E / G is matched against its candidates: its coefficient
 * rows in each PUBLISHED or SUPERSEDED agreement, or an implicit 0 in an agreement it is absent from. Runs of
 * N valid days matching a candidate are observations; from them come the application and leaving dates, the
 * statuses of the rows, the anomalies and the recommendations. Nothing here reads a store: the input carries
 * the days already read.
 */
public final class ObservedSharingCalculator {

    private static final Comparator<ObservedSupply> BY_CODE = Comparator.comparing(ObservedSupply::code)
            .thenComparing(ObservedSupply::id);

    private static final Comparator<SharingAnomaly> ANOMALY_ORDER = Comparator
            .comparing(SharingAnomaly::start, AgreementObservationSummarizer.CHRONOLOGICAL)
            .thenComparing(SharingAnomaly::supply, BY_CODE)
            .thenComparing(SharingAnomaly::type);

    private final int consecutiveDays;

    public ObservedSharingCalculator(int consecutiveDays) {
        if (consecutiveDays < 1) {
            throw new IllegalArgumentException("A run must last at least one valid day, not " + consecutiveDays);
        }
        this.consecutiveDays = consecutiveDays;
    }

    public PlantSharingObservation calculate(ObservationInput input) {
        List<ObservedAgreement> agreements = RegisteredOrder.sort(input.agreements().stream()
                        .filter(agreement -> agreement.status() != SharingAgreementStatus.DRAFT)
                        .toList(), ObservedAgreement::id, ObservedAgreement::distributorFileSentOn,
                ObservedAgreement::createdAt, RegisteredOrder.earliestValidFrom(input.coefficients(),
                        ObservedCoefficient::sharingAgreementId, ObservedCoefficient::validFrom, input.zone()));
        Set<UUID> agreementIds = agreements.stream().map(ObservedAgreement::id).collect(Collectors.toSet());
        List<ObservedCoefficient> coefficients = input.coefficients().stream()
                .filter(coefficient -> agreementIds.contains(coefficient.sharingAgreementId()))
                .toList();
        Set<UUID> notComputable = input.notComputableSupplies().stream()
                .map(ObservedSupply::id)
                .collect(Collectors.toSet());
        List<ObservedSupply> notComputableSupplies = input.notComputableSupplies().stream()
                .filter(supply -> !supply.id().equals(input.plantSupply().id()))
                .sorted(BY_CODE)
                .toList();
        // The plant's own supply is never a consumer (PLT-002).
        List<ObservedSupply> supplies = input.supplies().stream()
                .filter(supply -> !supply.id().equals(input.plantSupply().id()) && !notComputable.contains(supply.id()))
                .sorted(BY_CODE)
                .toList();

        if (input.analysedFrom() == null || input.analysedTo() == null) {
            return notComputable(agreements, notComputableSupplies);
        }

        ValidDayFilter filter = new ValidDayFilter(input.zone(), input.peakPowerKWp());
        NavigableMap<LocalDate, Double> plantValid =
                filter.plantValidDays(input.plantDays(), input.analysedFrom(), input.analysedTo());
        Map<UUID, ObservedAgreement> agreementsById = agreements.stream()
                .collect(Collectors.toMap(ObservedAgreement::id, Function.identity()));

        SupplyTimelineWalker walker = new SupplyTimelineWalker(consecutiveDays);
        AnomalyCollector collector = new AnomalyCollector(consecutiveDays);
        ObservationStatusResolver resolver = new ObservationStatusResolver(input.zone());

        Map<UUID, SupplyRows> rows = new HashMap<>();
        Map<UUID, SupplyTimeline> timelines = new HashMap<>();
        Map<UUID, List<Candidate>> candidatesBySupply = new HashMap<>();
        Map<UUID, Map<LocalDate, Double>> energy = new LinkedHashMap<>();
        Map<UUID, NavigableMap<LocalDate, Double>> betas = new HashMap<>();
        List<ObservedSupply> withData = new ArrayList<>();
        List<ObservedSupply> withoutData = new ArrayList<>();
        for (ObservedSupply supply : supplies) {
            List<Candidate> candidates = candidates(supply, agreements, coefficients);
            List<ValidDay> days = filter.supplyValidDays(plantValid,
                    input.supplyDays().getOrDefault(supply.id(), List.of()));
            SupplyTimeline timeline = walker.walk(days, candidates);
            timelines.put(supply.id(), timeline);
            candidatesBySupply.put(supply.id(), candidates);
            rows.put(supply.id(), resolver.resolve(supply, timeline, agreements, candidates));
            energy.put(supply.id(), days.stream()
                    .collect(Collectors.toMap(ValidDay::day, ValidDay::energyKWh, Double::sum, LinkedHashMap::new)));
            betas.put(supply.id(), days.stream()
                    .collect(Collectors.toMap(ValidDay::day, ValidDay::beta, (a, b) -> b, TreeMap::new)));
            (days.isEmpty() ? withoutData : withData).add(supply);
        }

        // The plant's own supply is never analysed, but its rows are listed, not observable (OBS-011).
        ObservedSupply plantSupply = input.plantSupply();
        rows.put(plantSupply.id(), resolver.notObservable(plantSupply, agreements,
                candidates(plantSupply, agreements, coefficients)));
        List<ObservedSupply> listed = new ArrayList<>(supplies);
        listed.add(plantSupply);
        listed.sort(BY_CODE);
        Set<UUID> notCounted = new HashSet<>(notComputable);
        notCounted.add(plantSupply.id());

        // The applied windows only rest on the rows' observed dates, so they are known before the anomalies.
        AgreementObservationSummarizer.AppliedWindows applied =
                AgreementObservationSummarizer.appliedWindows(agreements, listed, rows, input.analysedTo());
        List<SharingAnomaly> collected = new ArrayList<>();
        for (ObservedSupply supply : supplies) {
            collected.addAll(collector.collect(supply, timelines.get(supply.id()), candidatesBySupply.get(supply.id()),
                    agreementsById, applied.windows(), rows.get(supply.id())));
        }
        List<SharingAnomaly> anomalies = new SwapFinder().withSwaps(collected,
                candidatesByAgreement(candidatesBySupply), betas);
        anomalies.sort(ANOMALY_ORDER);

        List<AgreementObservation> observations = new AgreementObservationSummarizer().summarize(
                new AgreementObservationSummarizer.Context(agreements, listed, rows, anomalies, plantValid, energy,
                        betas, coefficients, notCounted, applied));
        return new PlantSharingObservation(
                new AnalysisStatus(true, null, input.analysedFrom(), input.analysedTo(), withData, withoutData,
                        notComputableSupplies),
                consecutiveDays, observations,
                anomalies.stream().filter(anomaly -> anomaly.attributedAgreementId() == null).toList());
    }

    /**
     * The supply's candidates, one per agreement, at its rank: its row in the agreement, or the agreement's
     * implicit 0. A supply holds at most one row in an agreement (#601).
     */
    private static List<Candidate> candidates(ObservedSupply supply, List<ObservedAgreement> agreements,
                                              List<ObservedCoefficient> coefficients) {
        List<Candidate> candidates = new ArrayList<>();
        for (int rank = 0; rank < agreements.size(); rank++) {
            UUID agreementId = agreements.get(rank).id();
            int agreementRank = rank;
            candidates.add(coefficients.stream()
                    .filter(row -> row.supplyId().equals(supply.id()) && row.sharingAgreementId().equals(agreementId))
                    .findFirst()
                    .map(row -> Candidate.of(row, agreementRank))
                    .orElseGet(() -> Candidate.implicitZero(agreementId, agreementRank)));
        }
        return candidates;
    }

    /**
     * Each supply's candidates keyed by agreement id: a supply has one candidate per agreement.
     */
    private static Map<UUID, Map<UUID, Candidate>> candidatesByAgreement(Map<UUID, List<Candidate>> candidatesBySupply) {
        Map<UUID, Map<UUID, Candidate>> byAgreement = new HashMap<>();
        candidatesBySupply.forEach((supplyId, candidates) -> byAgreement.put(supplyId, candidates.stream()
                .collect(Collectors.toMap(Candidate::sharingAgreementId, Function.identity()))));
        return byAgreement;
    }

    /**
     * Without data from the plant supply nothing can be observed: the agreements are listed, with nothing
     * observed and nothing recommended (OBS-006).
     */
    private PlantSharingObservation notComputable(List<ObservedAgreement> agreements,
                                                  List<ObservedSupply> notComputableSupplies) {
        List<AgreementObservation> observations = agreements.stream()
                .map(agreement -> new AgreementObservation(agreement, null, null,
                        AgreementObservationSummarizer.countStatuses(List.of()),
                        AgreementObservationSummarizer.countStatuses(List.of()),
                        AgreementObservationSummarizer.countStatuses(List.of()), List.of(),
                        AgreementObservationSummarizer.countTypes(List.of()), null, List.of(), List.of(), List.of()))
                .toList();
        return new PlantSharingObservation(
                new AnalysisStatus(false, NotComputableReason.PLANT_SUPPLY_WITHOUT_DATA, null, null, List.of(),
                        List.of(), notComputableSupplies),
                consecutiveDays, observations, List.of());
    }
}
