package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The order the agreements were registered in, used where nothing observed places them (OBS-003): the day the
 * distributor file was sent, else the earliest stored start of its rows as a local day; then the creation
 * instant; then the id. Agreements with neither date come after the dated ones.
 */
final class RegisteredOrder {

    private RegisteredOrder() {
    }

    static List<ObservedAgreement> sort(List<ObservedAgreement> agreements, List<ObservedCoefficient> coefficients,
                                        ZoneId zone) {
        Map<UUID, LocalDate> earliestValidFrom = coefficients.stream()
                .filter(coefficient -> coefficient.validFrom() != null)
                .collect(Collectors.toMap(ObservedCoefficient::sharingAgreementId,
                        coefficient -> localDay(coefficient.validFrom(), zone),
                        (a, b) -> a.isBefore(b) ? a : b));
        Comparator<ObservedAgreement> order = Comparator
                .comparing((ObservedAgreement agreement) -> agreement.distributorFileSentOn() != null
                                ? agreement.distributorFileSentOn() : earliestValidFrom.get(agreement.id()),
                        Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
                .thenComparing(ObservedAgreement::createdAt, Comparator.nullsLast(Comparator.<Instant>naturalOrder()))
                .thenComparing(ObservedAgreement::id);
        return agreements.stream().sorted(order).toList();
    }

    static LocalDate localDay(Instant instant, ZoneId zone) {
        return instant == null ? null : instant.atZone(zone).toLocalDate();
    }
}
