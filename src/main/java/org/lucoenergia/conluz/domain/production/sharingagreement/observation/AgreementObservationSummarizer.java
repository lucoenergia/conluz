package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import org.lucoenergia.conluz.domain.production.sharingagreement.RegisteredOrder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Gathers each agreement's rows and anomalies into its observation (OBS-005): its observed dates, the counts,
 * the share of production no supply with data received while it was applied, and the recommendations.
 */
final class AgreementObservationSummarizer {

    static final Comparator<ObservedDate> CHRONOLOGICAL = Comparator.comparing(ObservedDate::to)
            .thenComparing(ObservedDate::from, Comparator.nullsFirst(Comparator.naturalOrder()));

    /**
     * @param agreements    the agreements in scope, in registered order
     * @param supplies      the supplies whose rows are listed, in that order: the analysed ones and the plant's own
     * @param rows          each listed supply's rows, keyed by supply id
     * @param anomalies     the anomalies of every analysed supply
     * @param plantValid    the plant's valid days and their production
     * @param energy        each analysed supply's energy on its valid days, keyed by supply id
     * @param betas         each analysed supply's observed β on its valid days, keyed by supply id
     * @param coefficients  the rows of the agreements in scope, not-computable supplies included
     * @param notCounted    the supplies whose energy is not counted: the not-computable ones and the plant's own
     */
    record Context(List<ObservedAgreement> agreements, List<ObservedSupply> supplies, Map<UUID, SupplyRows> rows,
                   List<SharingAnomaly> anomalies, NavigableMap<LocalDate, Double> plantValid,
                   Map<UUID, Map<LocalDate, Double>> energy, Map<UUID, NavigableMap<LocalDate, Double>> betas,
                   List<ObservedCoefficient> coefficients,
                   Set<UUID> notCounted, LocalDate analysedTo, ZoneId zone) {
    }

    List<AgreementObservation> summarize(Context context) {
        Map<UUID, ObservedDate[]> datesByAgreement = new HashMap<>();
        for (ObservedAgreement agreement : context.agreements()) {
            List<ObservedDate> observed = context.supplies().stream()
                    .flatMap(supply -> context.rows().get(supply.id()).coefficients()
                            .getOrDefault(agreement.id(), List.of()).stream())
                    .map(CoefficientObservation::observedDate)
                    .filter(Objects::nonNull)
                    .toList();
            datesByAgreement.put(agreement.id(), new ObservedDate[]{earliest(observed), mostFrequent(observed)});
        }
        Map<UUID, LocalDate[]> windows = windows(context, datesByAgreement);

        List<AgreementObservation> observations = new ArrayList<>();
        for (ObservedAgreement agreement : context.agreements()) {
            ObservedDate mostFrequent = datesByAgreement.get(agreement.id())[1];
            LocalDate[] window = windows.get(agreement.id());
            List<CoefficientObservation> coefficients = new ArrayList<>();
            List<LeavingObservation> leavings = new ArrayList<>();
            for (ObservedSupply supply : context.supplies()) {
                SupplyRows rows = context.rows().get(supply.id());
                NavigableMap<LocalDate, Double> betas = context.betas().get(supply.id());
                for (CoefficientObservation row : rows.coefficients().getOrDefault(agreement.id(), List.of())) {
                    // A row without an observed date, a not observable one included, keeps the whole window.
                    LocalDate[] rowWindow = rowWindow(row.observedDate(), window);
                    if (row.status() == ObservationStatus.NOT_OBSERVABLE) {
                        // Never observed: it has no date and no observed β to set (OBS-011).
                        coefficients.add(new CoefficientObservation(row.coefficientId(), row.supply(),
                                row.coefficient(), row.storedValidFrom(), row.storedValidTo(), row.status(), null,
                                null, row.observedBeta(), windowDays(context.plantValid(), rowWindow),
                                firstDay(rowWindow), lastDay(rowWindow)));
                        continue;
                    }
                    coefficients.add(new CoefficientObservation(row.coefficientId(), row.supply(), row.coefficient(),
                            row.storedValidFrom(), row.storedValidTo(), row.status(), row.observedDate(),
                            row.status() == ObservationStatus.NOT_DISTINGUISHABLE ? mostFrequent : row.proposedDate(),
                            observedBeta(betas, rowWindow), windowDays(context.plantValid(), rowWindow),
                            firstDay(rowWindow), lastDay(rowWindow)));
                }
                LeavingObservation leaving = rows.leavings().get(agreement.id());
                if (leaving != null) {
                    LocalDate[] rowWindow = rowWindow(leaving.observedLeavingDate(), window);
                    leavings.add(new LeavingObservation(leaving.supply(), leaving.previousAgreement(),
                            leaving.previousCoefficientId(), leaving.previousCoefficient(),
                            leaving.observedLeavingDate(), leaving.storedValidTo(), leaving.status(),
                            leaving.status() == ObservationStatus.NOT_DISTINGUISHABLE
                                    ? mostFrequent : leaving.proposedDate(),
                            observedBeta(betas, rowWindow), windowDays(context.plantValid(), rowWindow),
                            firstDay(rowWindow), lastDay(rowWindow)));
                }
            }
            List<SharingAnomaly> anomalies = context.anomalies().stream()
                    .filter(anomaly -> agreement.id().equals(anomaly.attributedAgreementId()))
                    .toList();
            observations.add(new AgreementObservation(agreement, datesByAgreement.get(agreement.id())[0],
                    mostFrequent, countStatuses(coefficients.stream().map(CoefficientObservation::status).toList()),
                    countStatuses(leavings.stream().map(LeavingObservation::status).toList()), anomalies,
                    countTypes(anomalies), unallocatedShare(context, agreement, window),
                    recommendations(agreement, coefficients, leavings, anomalies), coefficients, leavings));
        }
        return observations;
    }

    /**
     * The days each agreement is taken as applied to the plant: from its most frequent observed date to the day
     * before the next agreement's, or to the end of the analysed range. None when it was never observed.
     */
    private static Map<UUID, LocalDate[]> windows(Context context, Map<UUID, ObservedDate[]> datesByAgreement) {
        List<ObservedAgreement> observed = context.agreements().stream()
                .filter(agreement -> datesByAgreement.get(agreement.id())[1] != null)
                .sorted(Comparator.comparing(agreement -> datesByAgreement.get(agreement.id())[1].to()))
                .toList();
        Map<UUID, LocalDate[]> windows = new HashMap<>();
        for (int i = 0; i < observed.size(); i++) {
            LocalDate from = datesByAgreement.get(observed.get(i).id())[1].to();
            LocalDate to = i + 1 < observed.size()
                    ? datesByAgreement.get(observed.get(i + 1).id())[1].to().minusDays(1)
                    : context.analysedTo();
            windows.put(observed.get(i).id(), new LocalDate[]{from, to});
        }
        return windows;
    }

    /**
     * What a supply received under a row (OBS-009): the median of its observed β on its valid days in the row's
     * window. {@link ObservedBeta#NONE} when the row has no window or the supply has no valid day in it.
     */
    private static ObservedBeta observedBeta(NavigableMap<LocalDate, Double> betas, LocalDate[] rowWindow) {
        if (rowWindow == null) {
            return ObservedBeta.NONE;
        }
        return ObservedBeta.of(betas.subMap(rowWindow[0], true, rowWindow[1], true).values());
    }

    /**
     * The plant-valid days in a row's window, 0 when it has none (OBS-013). Never fewer than its supply's valid days
     * there, since those are plant-valid days too.
     */
    private static int windowDays(NavigableMap<LocalDate, Double> plantValid, LocalDate[] rowWindow) {
        return rowWindow == null ? 0 : plantValid.subMap(rowWindow[0], true, rowWindow[1], true).size();
    }

    private static LocalDate firstDay(LocalDate[] rowWindow) {
        return rowWindow == null ? null : rowWindow[0];
    }

    private static LocalDate lastDay(LocalDate[] rowWindow) {
        return rowWindow == null ? null : rowWindow[1];
    }

    /**
     * The days a row is taken as applied to its supply: from its observed date (the last day it may have started)
     * to the end of the agreement's window, or the whole window when the row has no observed date. Null when the
     * agreement has no window or the row starts after it ends.
     */
    private static LocalDate[] rowWindow(ObservedDate observed, LocalDate[] window) {
        if (window == null || window[1].isBefore(window[0])) {
            return null;
        }
        LocalDate from = observed == null ? window[0] : observed.to();
        if (from.isAfter(window[1])) {
            return null;
        }
        return new LocalDate[]{from, window[1]};
    }

    /**
     * 1 − ΣE / ΣG over the plant's valid days in the agreement's window, and the share it registers for the
     * supplies without a valid day, the not-computable ones and the plant's own supply included (OBS-011), both
     * weighted by production. Null when no valid day falls in the window.
     */
    private static UnallocatedShare unallocatedShare(Context context, ObservedAgreement agreement, LocalDate[] window) {
        if (window == null || window[1].isBefore(window[0])) {
            return null;
        }
        Map<UUID, List<ObservedCoefficient>> rowsBySupply = new LinkedHashMap<>();
        for (ObservedCoefficient coefficient : context.coefficients()) {
            if (coefficient.sharingAgreementId().equals(agreement.id())
                    && (context.energy().containsKey(coefficient.supplyId())
                    || context.notCounted().contains(coefficient.supplyId()))) {
                rowsBySupply.computeIfAbsent(coefficient.supplyId(), id -> new ArrayList<>()).add(coefficient);
            }
        }
        double production = 0;
        double energy = 0;
        double weightedShareWithoutData = 0;
        int days = 0;
        for (Map.Entry<LocalDate, Double> day : context.plantValid().subMap(window[0], true, window[1], true).entrySet()) {
            double g = day.getValue();
            production += g;
            days++;
            for (Map<LocalDate, Double> supplyEnergy : context.energy().values()) {
                energy += supplyEnergy.getOrDefault(day.getKey(), 0d);
            }
            double shareWithoutData = 0;
            for (Map.Entry<UUID, List<ObservedCoefficient>> rows : rowsBySupply.entrySet()) {
                Map<LocalDate, Double> supplyEnergy = context.energy().get(rows.getKey());
                if (supplyEnergy == null || !supplyEnergy.containsKey(day.getKey())) {
                    shareWithoutData += valueOn(rows.getValue(), day.getKey(), context.zone());
                }
            }
            weightedShareWithoutData += g * shareWithoutData;
        }
        if (days == 0) {
            return null;
        }
        return new UnallocatedShare(1 - energy / production, weightedShareWithoutData / production, days, window[0],
                window[1]);
    }

    /**
     * A supply's registered value on a day: its only row, or, when the agreement holds several, the one whose
     * stored period holds the day.
     */
    private static double valueOn(List<ObservedCoefficient> rows, LocalDate day, ZoneId zone) {
        if (rows.size() == 1) {
            return rows.get(0).coefficient().doubleValue();
        }
        for (ObservedCoefficient row : rows) {
            LocalDate from = RegisteredOrder.localDay(row.validFrom(), zone);
            LocalDate to = RegisteredOrder.localDay(row.validTo(), zone);
            if (from != null && !day.isBefore(from) && (to == null || day.isBefore(to))) {
                return row.coefficient().doubleValue();
            }
        }
        return 0d;
    }

    private static List<SharingRecommendation> recommendations(ObservedAgreement agreement,
                                                               List<CoefficientObservation> coefficients,
                                                               List<LeavingObservation> leavings,
                                                               List<SharingAnomaly> anomalies) {
        List<ObservationStatus> statuses = new ArrayList<>();
        coefficients.forEach(row -> statuses.add(row.status()));
        leavings.forEach(row -> statuses.add(row.status()));

        List<SharingRecommendation> recommendations = new ArrayList<>();
        if (statuses.contains(ObservationStatus.APPLIED_DIFFERENT_DATE)
                || statuses.contains(ObservationStatus.NOT_DISTINGUISHABLE)) {
            recommendations.add(SharingRecommendation.SET_DATES);
        }
        boolean anyObserved = statuses.contains(ObservationStatus.APPLIED_SAME_DATE)
                || statuses.contains(ObservationStatus.APPLIED_DIFFERENT_DATE);
        if (!anyObserved && agreement.distributorFileSentOn() == null) {
            recommendations.add(SharingRecommendation.DEACTIVATE);
        }
        if (!anomalies.isEmpty()) {
            recommendations.add(SharingRecommendation.REVIEW_ANOMALIES);
        }
        if (recommendations.isEmpty()) {
            recommendations.add(SharingRecommendation.NONE);
        }
        return recommendations;
    }

    private static ObservedDate earliest(List<ObservedDate> dates) {
        return dates.stream().min(CHRONOLOGICAL).orElse(null);
    }

    /**
     * The date observed most often; a tie goes to the earliest.
     */
    private static ObservedDate mostFrequent(List<ObservedDate> dates) {
        Map<ObservedDate, Integer> counts = new HashMap<>();
        dates.forEach(date -> counts.merge(date, 1, Integer::sum));
        return counts.entrySet().stream()
                .min(Comparator.<Map.Entry<ObservedDate, Integer>>comparingInt(entry -> -entry.getValue())
                        .thenComparing(Map.Entry::getKey, CHRONOLOGICAL))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    static Map<ObservationStatus, Integer> countStatuses(List<ObservationStatus> statuses) {
        Map<ObservationStatus, Integer> counts = new EnumMap<>(ObservationStatus.class);
        for (ObservationStatus status : ObservationStatus.values()) {
            counts.put(status, 0);
        }
        statuses.forEach(status -> counts.merge(status, 1, Integer::sum));
        return counts;
    }

    static Map<SharingAnomalyType, Integer> countTypes(List<SharingAnomaly> anomalies) {
        Map<SharingAnomalyType, Integer> counts = new EnumMap<>(SharingAnomalyType.class);
        for (SharingAnomalyType type : SharingAnomalyType.values()) {
            counts.put(type, 0);
        }
        anomalies.forEach(anomaly -> counts.merge(anomaly.type(), 1, Integer::sum));
        return counts;
    }
}
