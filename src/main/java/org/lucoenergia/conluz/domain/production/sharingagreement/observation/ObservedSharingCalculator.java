package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import org.lucoenergia.conluz.domain.production.sharingagreement.RegisteredOrder;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
                .filter(supply -> !supply.id().equals(input.plantSupplyId()))
                .sorted(BY_CODE)
                .toList();
        // The plant's own supply is never a consumer (PLT-002).
        List<ObservedSupply> supplies = input.supplies().stream()
                .filter(supply -> !supply.id().equals(input.plantSupplyId()) && !notComputable.contains(supply.id()))
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
        Map<UUID, Map<LocalDate, Double>> energy = new LinkedHashMap<>();
        Map<UUID, NavigableMap<LocalDate, Double>> betas = new HashMap<>();
        List<SharingAnomaly> anomalies = new ArrayList<>();
        List<ObservedSupply> withData = new ArrayList<>();
        List<ObservedSupply> withoutData = new ArrayList<>();
        for (ObservedSupply supply : supplies) {
            List<Candidate> candidates = candidates(supply, agreements, coefficients);
            List<ValidDay> days = filter.supplyValidDays(plantValid,
                    input.supplyDays().getOrDefault(supply.id(), List.of()));
            SupplyTimeline timeline = walker.walk(days, candidates);
            anomalies.addAll(collector.collect(supply, timeline, agreementsById));
            rows.put(supply.id(), resolver.resolve(supply, timeline, agreements, candidates));
            energy.put(supply.id(), days.stream()
                    .collect(Collectors.toMap(ValidDay::day, ValidDay::energyKWh, Double::sum, LinkedHashMap::new)));
            betas.put(supply.id(), days.stream()
                    .collect(Collectors.toMap(ValidDay::day, ValidDay::beta, (a, b) -> b, TreeMap::new)));
            (days.isEmpty() ? withoutData : withData).add(supply);
        }
        anomalies.sort(ANOMALY_ORDER);

        List<AgreementObservation> observations = new AgreementObservationSummarizer().summarize(
                new AgreementObservationSummarizer.Context(agreements, supplies, rows, anomalies, plantValid, energy,
                        betas, coefficients, notComputable, input.analysedTo(), input.zone()));
        return new PlantSharingObservation(
                new AnalysisStatus(true, null, input.analysedFrom(), input.analysedTo(), withData, withoutData,
                        notComputableSupplies),
                consecutiveDays, observations,
                anomalies.stream().filter(anomaly -> anomaly.attributedAgreementId() == null).toList());
    }

    /**
     * The supply's candidates: one per row it holds in each agreement, or the agreement's implicit 0.
     */
    private static List<Candidate> candidates(ObservedSupply supply, List<ObservedAgreement> agreements,
                                              List<ObservedCoefficient> coefficients) {
        List<Candidate> candidates = new ArrayList<>();
        for (int rank = 0; rank < agreements.size(); rank++) {
            UUID agreementId = agreements.get(rank).id();
            List<ObservedCoefficient> rows = coefficients.stream()
                    .filter(row -> row.supplyId().equals(supply.id()) && row.sharingAgreementId().equals(agreementId))
                    .sorted(Comparator.comparing(ObservedCoefficient::validFrom,
                                    Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(ObservedCoefficient::id))
                    .toList();
            if (rows.isEmpty()) {
                candidates.add(Candidate.implicitZero(agreementId, rank));
            } else {
                for (ObservedCoefficient row : rows) {
                    candidates.add(Candidate.of(row, rank));
                }
            }
        }
        return candidates;
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
                        AgreementObservationSummarizer.countStatuses(List.of()), List.of(),
                        AgreementObservationSummarizer.countTypes(List.of()), null, List.of(), List.of(), List.of()))
                .toList();
        return new PlantSharingObservation(
                new AnalysisStatus(false, NotComputableReason.PLANT_SUPPLY_WITHOUT_DATA, null, null, List.of(),
                        List.of(), notComputableSupplies),
                consecutiveDays, observations, List.of());
    }
}
