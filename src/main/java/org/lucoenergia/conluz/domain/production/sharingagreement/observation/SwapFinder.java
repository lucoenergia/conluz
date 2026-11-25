package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.UUID;

/**
 * Finds the supplies swapped in an agreement (OBS-018): two supplies whose coefficients are not applied to them in
 * that agreement, each receiving the other's value in it while the other one receives its own.
 */
final class SwapFinder {

    /**
     * @param candidates each supply's candidate in each agreement, keyed by supply id, then by agreement id
     * @param betas      each analysed supply's observed β on its valid days, keyed by supply id
     * @return the anomalies in the same order, a {@link SharingAnomalyType#NOT_APPLIED} one naming the supply it is
     * swapped with when exactly one supply is reciprocal with it
     */
    List<SharingAnomaly> withSwaps(List<SharingAnomaly> anomalies, Map<UUID, Map<UUID, Candidate>> candidates,
                                   Map<UUID, NavigableMap<LocalDate, Double>> betas) {
        List<SharingAnomaly> notApplied = anomalies.stream()
                .filter(anomaly -> anomaly.type() == SharingAnomalyType.NOT_APPLIED)
                .toList();
        List<SharingAnomaly> result = new ArrayList<>(anomalies.size());
        for (SharingAnomaly anomaly : anomalies) {
            if (anomaly.type() != SharingAnomalyType.NOT_APPLIED) {
                result.add(anomaly);
                continue;
            }
            Set<ObservedSupply> partners = new LinkedHashSet<>();
            for (SharingAnomaly other : notApplied) {
                if (reciprocal(anomaly, other, candidates, betas)) {
                    partners.add(other.supply());
                }
            }
            // Several supplies would be a swap among more than two: naming one of them would be a guess.
            result.add(partners.size() == 1 ? anomaly.swappedWith(partners.iterator().next()) : anomaly);
        }
        return result;
    }

    /**
     * Whether two anomalies of different supplies, not applied in the same agreement, overlap on a valid day of
     * both and each receives the other supply's value in the agreement.
     */
    private static boolean reciprocal(SharingAnomaly anomaly, SharingAnomaly other,
                                      Map<UUID, Map<UUID, Candidate>> candidates,
                                      Map<UUID, NavigableMap<LocalDate, Double>> betas) {
        UUID agreementId = anomaly.attributedAgreementId();
        if (other.supply().id().equals(anomaly.supply().id()) || !agreementId.equals(other.attributedAgreementId())) {
            return false;
        }
        Candidate own = candidates.get(anomaly.supply().id()).get(agreementId);
        Candidate others = candidates.get(other.supply().id()).get(agreementId);
        return others.matches(anomaly.observedBeta().value().doubleValue())
                && own.matches(other.observedBeta().value().doubleValue())
                && overlap(anomaly, other, betas);
    }

    private static boolean overlap(SharingAnomaly anomaly, SharingAnomaly other,
                                   Map<UUID, NavigableMap<LocalDate, Double>> betas) {
        NavigableMap<LocalDate, Double> othersDays = days(other, betas);
        return days(anomaly, betas).keySet().stream().anyMatch(othersDays::containsKey);
    }

    /**
     * The anomaly's valid days: from its first day to its last, or to the supply's last valid day when it is open.
     */
    private static NavigableMap<LocalDate, Double> days(SharingAnomaly anomaly,
                                                        Map<UUID, NavigableMap<LocalDate, Double>> betas) {
        NavigableMap<LocalDate, Double> supplyDays = betas.get(anomaly.supply().id());
        return anomaly.end() == null
                ? supplyDays.tailMap(anomaly.start().to(), true)
                : supplyDays.subMap(anomaly.start().to(), true, anomaly.end(), true);
    }
}
