package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns a supply's timeline into anomalies (OBS-004): sustained runs of anomalous days merged per type, the
 * reverted applications, and the applications before the distributor file was sent.
 */
final class AnomalyCollector {

    private final int consecutiveDays;

    AnomalyCollector(int consecutiveDays) {
        this.consecutiveDays = consecutiveDays;
    }

    List<SharingAnomaly> collect(ObservedSupply supply, SupplyTimeline timeline,
                                 Map<UUID, ObservedAgreement> agreements) {
        List<SharingAnomaly> anomalies = new ArrayList<>();
        collectStreaks(supply, timeline, anomalies);
        for (SupplyTimeline.Observation observation : timeline.observations()) {
            if (observation.reverted()) {
                anomalies.add(anomaly(supply, timeline, SharingAnomalyType.REVERTED, observation.date(),
                        lastDayCurrent(timeline, observation), observation.previous().sharingAgreementId()));
            }
            if (observation.first()) {
                LocalDate sentOn = agreements.get(observation.candidate().sharingAgreementId()).distributorFileSentOn();
                if (sentOn != null && observation.date().to().isBefore(sentOn)) {
                    LocalDate lastDayCurrent = lastDayCurrent(timeline, observation);
                    LocalDate dayBeforeSent = sentOn.minusDays(1);
                    LocalDate end = lastDayCurrent == null || lastDayCurrent.isAfter(dayBeforeSent)
                            ? dayBeforeSent : lastDayCurrent;
                    // Attributed to the agreement sent late, not to the one applied before it.
                    anomalies.add(anomaly(supply, timeline, SharingAnomalyType.APPLIED_BEFORE_SENT, observation.date(),
                            end, observation.candidate().sharingAgreementId()));
                }
            }
        }
        return anomalies;
    }

    /**
     * Consecutive valid days of one anomalous type, kept when they last N days.
     */
    private void collectStreaks(ObservedSupply supply, SupplyTimeline timeline, List<SharingAnomaly> anomalies) {
        SharingAnomalyType[] types = timeline.anomalousTypes();
        List<ValidDay> days = timeline.days();
        int i = 0;
        while (i < types.length) {
            SharingAnomalyType type = types[i];
            if (type == null) {
                i++;
                continue;
            }
            int last = i;
            while (last + 1 < types.length && types[last + 1] == type) {
                last++;
            }
            if (last - i + 1 >= consecutiveDays) {
                ObservedDate start = i == 0
                        ? ObservedDate.onOrBefore(days.get(i).day())
                        : new ObservedDate(days.get(i - 1).day().plusDays(1), days.get(i).day());
                LocalDate end = last == types.length - 1 ? null : days.get(last).day();
                Candidate current = timeline.currentBefore()[i];
                anomalies.add(anomaly(supply, timeline, type, start, end,
                        current == null ? null : current.sharingAgreementId()));
            }
            i = last + 1;
        }
    }

    /**
     * The last valid day an observed candidate stayed applied: the day before the next observation's run, or
     * null when it is still applied at the end of the data.
     */
    private static LocalDate lastDayCurrent(SupplyTimeline timeline, SupplyTimeline.Observation observation) {
        return timeline.next(observation)
                .map(next -> timeline.days().get(next.runStartIndex() - 1).day())
                .orElse(null);
    }

    private static SharingAnomaly anomaly(ObservedSupply supply, SupplyTimeline timeline, SharingAnomalyType type,
                                          ObservedDate start, LocalDate end, UUID attributedAgreementId) {
        return new SharingAnomaly(idOf(supply.id(), type, start.to()), supply, type, start, end, attributedAgreementId,
                observedBeta(timeline, start.to(), end));
    }

    /**
     * What the supply received over the anomaly's valid days (OBS-009): from its first day (the end of a start
     * range) to its last, or to the supply's last valid day when it is open. For a streak these are exactly the
     * anomalous days.
     */
    private static ObservedBeta observedBeta(SupplyTimeline timeline, LocalDate first, LocalDate last) {
        return ObservedBeta.of(timeline.days().stream()
                .filter(day -> !day.day().isBefore(first) && (last == null || !day.day().isAfter(last)))
                .map(ValidDay::beta)
                .toList());
    }

    /**
     * Derived from the supply, the type and the first anomalous day, so an unchanged anomaly keeps its id from
     * one call to the next.
     */
    static UUID idOf(UUID supplyId, SharingAnomalyType type, LocalDate startDay) {
        return UUID.nameUUIDFromBytes((supplyId + "|" + type.name() + "|" + startDay)
                .getBytes(StandardCharsets.UTF_8));
    }
}
