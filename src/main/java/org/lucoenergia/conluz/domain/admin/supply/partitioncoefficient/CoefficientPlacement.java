package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Places a coefficient at a new validFrom within its {@code (plant, supply)} timeline, chronologically (SHA-003): it
 * ends where the next coefficient starts, or stays open, and the previous coefficient's end is moved back to it only
 * when that end is open or later. A period is only ever shortened by a placement, never extended (D1 of #582).
 *
 * <p>Correcting an active coefficient first detaches it (SHA-004): the coefficient that ended where it started takes
 * its former end, exactly as a deactivation splices the chain. That coefficient's end was cascade-derived, since it
 * is adjacent to the moved coefficient. A close the coefficient carries itself and that was not cascade-derived is
 * kept, shortened to the next start if that comes earlier.
 *
 * <p>Whether an end was cascade-derived is inferred from adjacency only, as in {@link CoefficientSuccessionCascade}:
 * an authored close that happens to equal the next start is indistinguishable from a cascade-derived one.
 *
 * <p>Pure: it reads only the rows it is given and writes nothing. Pending rows (no validFrom) are not part of the
 * timeline and are ignored.
 */
public final class CoefficientPlacement {

    private CoefficientPlacement() {
    }

    /**
     * The outcome of a placement: the rows to write, or why the coefficient cannot be placed at that date.
     */
    public sealed interface Result permits Placed, StartTaken, NotBeforeOwnClose {
    }

    /**
     * The coefficient placed, first, followed by every other row of the timeline whose period changed.
     */
    public record Placed(List<SupplyPartitionCoefficient> writes) implements Result {
    }

    /**
     * Another coefficient of the timeline already starts at the requested date (SHA-005).
     */
    public record StartTaken(SupplyPartitionCoefficient conflictingWith) implements Result {
    }

    /**
     * The requested date is on or after the close the coefficient carries itself, which a placement keeps.
     */
    public record NotBeforeOwnClose() implements Result {
    }

    /**
     * @param timeline     every row of the coefficient's {@code (plant, supply)} timeline, the coefficient included,
     *                     as it stands before this placement
     * @param coefficient  the coefficient to place, as it stands in {@code timeline}
     * @param newValidFrom the start to place it at; never null
     */
    public static Result place(List<SupplyPartitionCoefficient> timeline, SupplyPartitionCoefficient coefficient,
                               Instant newValidFrom) {
        Objects.requireNonNull(newValidFrom, "newValidFrom");
        Map<UUID, SupplyPartitionCoefficient> others = new LinkedHashMap<>();
        timeline.stream()
                .filter(row -> row.getValidFrom() != null)
                .filter(row -> !row.getId().equals(coefficient.getId()))
                .forEach(row -> others.put(row.getId(), row));

        Optional<SupplyPartitionCoefficient> sameStart = others.values().stream()
                .filter(row -> row.getValidFrom().equals(newValidFrom))
                .findFirst();
        if (sameStart.isPresent()) {
            return new StartTaken(sameStart.get());
        }

        Instant ownClose = authoredClose(coefficient, others.values());
        if (ownClose != null && !newValidFrom.isBefore(ownClose)) {
            return new NotBeforeOwnClose();
        }

        Map<UUID, SupplyPartitionCoefficient> projected = new LinkedHashMap<>(others);
        if (coefficient.getValidFrom() != null) {
            detach(projected, coefficient);
        }

        Optional<SupplyPartitionCoefficient> previous = projected.values().stream()
                .filter(row -> row.getValidFrom().isBefore(newValidFrom))
                .max(Comparator.comparing(SupplyPartitionCoefficient::getValidFrom));
        Optional<SupplyPartitionCoefficient> next = projected.values().stream()
                .filter(row -> row.getValidFrom().isAfter(newValidFrom))
                .min(Comparator.comparing(SupplyPartitionCoefficient::getValidFrom));

        Instant newValidTo = next.map(SupplyPartitionCoefficient::getValidFrom).orElse(null);
        if (ownClose != null && (newValidTo == null || ownClose.isBefore(newValidTo))) {
            newValidTo = ownClose;
        }

        previous.filter(row -> row.getValidTo() == null || row.getValidTo().isAfter(newValidFrom))
                .ifPresent(row -> projected.put(row.getId(), withPeriod(row, row.getValidFrom(), newValidFrom)));

        List<SupplyPartitionCoefficient> writes = new ArrayList<>();
        writes.add(withPeriod(coefficient, newValidFrom, newValidTo));
        for (SupplyPartitionCoefficient row : projected.values()) {
            if (!Objects.equals(row.getValidTo(), others.get(row.getId()).getValidTo())) {
                writes.add(row);
            }
        }
        return new Placed(writes);
    }

    /**
     * The coefficient's own end, when it is a close an author set: not null, and not where the next row starts.
     */
    private static Instant authoredClose(SupplyPartitionCoefficient coefficient,
                                         Collection<SupplyPartitionCoefficient> others) {
        if (coefficient.getValidFrom() == null || coefficient.getValidTo() == null) {
            return null;
        }
        Optional<SupplyPartitionCoefficient> nextActivated = others.stream()
                .filter(row -> row.getValidFrom().isAfter(coefficient.getValidFrom()))
                .min(Comparator.comparing(SupplyPartitionCoefficient::getValidFrom));
        return CoefficientSuccessionCascade.isValidToCascadeDerived(coefficient, nextActivated)
                ? null : coefficient.getValidTo();
    }

    /**
     * Joins the coefficient's former neighbours: the row that ended where it started takes its former end.
     */
    private static void detach(Map<UUID, SupplyPartitionCoefficient> projected, SupplyPartitionCoefficient coefficient) {
        projected.values().stream()
                .filter(row -> coefficient.getValidFrom().equals(row.getValidTo()))
                .findFirst()
                .ifPresent(row -> projected.put(row.getId(),
                        withPeriod(row, row.getValidFrom(), coefficient.getValidTo())));
    }

    private static SupplyPartitionCoefficient withPeriod(SupplyPartitionCoefficient coefficient,
                                                         Instant validFrom, Instant validTo) {
        return new SupplyPartitionCoefficient.Builder()
                .withId(coefficient.getId())
                .withSupplyId(coefficient.getSupplyId())
                .withPlantId(coefficient.getPlantId())
                .withSharingAgreementId(coefficient.getSharingAgreementId())
                .withCoefficient(coefficient.getCoefficient())
                .withValidFrom(validFrom)
                .withValidTo(validTo)
                .withCreatedAt(coefficient.getCreatedAt())
                .build();
    }
}
