package org.lucoenergia.conluz.domain.production.sharingagreement;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The order the agreements of a plant were registered in, used where nothing observed places them (OBS-003): the
 * day the distributor file was sent, else the earliest stored start of its rows as a local day; then the creation
 * instant; then the id. Agreements with neither date come after the dated ones.
 *
 * <p>Type-neutral, so the observed sharing and the coefficients of an agreement order the same agreements the same
 * way, whatever each one reads them as.</p>
 */
public final class RegisteredOrder {

    private RegisteredOrder() {
    }

    /**
     * {@code agreements} in registered order.
     *
     * @param earliestValidFrom the earliest stored start of each agreement's rows as a local day, by agreement id;
     *                          an agreement with no activated row is absent
     */
    public static <A> List<A> sort(Collection<A> agreements, Function<A, UUID> id,
                                   Function<A, LocalDate> distributorFileSentOn, Function<A, Instant> createdAt,
                                   Map<UUID, LocalDate> earliestValidFrom) {
        Comparator<A> order = Comparator
                .comparing((A agreement) -> distributorFileSentOn.apply(agreement) != null
                                ? distributorFileSentOn.apply(agreement) : earliestValidFrom.get(id.apply(agreement)),
                        Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
                .thenComparing(createdAt, Comparator.nullsLast(Comparator.<Instant>naturalOrder()))
                .thenComparing(id);
        return agreements.stream().sorted(order).toList();
    }

    /**
     * The earliest stored start of each agreement's rows, as a local day in {@code zone}, by agreement id. Pending
     * rows have no start and are ignored.
     */
    public static <C> Map<UUID, LocalDate> earliestValidFrom(Collection<C> coefficients,
                                                             Function<C, UUID> sharingAgreementId,
                                                             Function<C, Instant> validFrom, ZoneId zone) {
        return coefficients.stream()
                .filter(coefficient -> validFrom.apply(coefficient) != null)
                .collect(Collectors.toMap(sharingAgreementId,
                        coefficient -> Objects.requireNonNull(localDay(validFrom.apply(coefficient), zone)),
                        (a, b) -> a.isBefore(b) ? a : b));
    }

    public static LocalDate localDay(Instant instant, ZoneId zone) {
        return instant == null ? null : instant.atZone(zone).toLocalDate();
    }
}
