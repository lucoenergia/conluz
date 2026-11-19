package org.lucoenergia.conluz.domain.production.sharingagreement.dates;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientOverlapDetector;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientPlacement;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.LeavingSupplyClosure;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.activation.CoefficientActivationErrorCode;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesEffects.AgreementStatusChange;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesEffects.CoefficientPeriodChange;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesEffects.CommunityStartChange;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesEffects.LeavingClosureChange;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesEffects.NeighbourChange;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Computes what setting an agreement's coefficient dates writes, and every effect of it, on the plant's timeline
 * projected in memory. The preview and the write run this same computation, so the preview's effects are the write's
 * (SHA-014).
 *
 * <ol>
 *     <li>Each coefficient is placed per SHA-003 to SHA-005 in ascending validFrom order, then by coefficient id, each
 *     on the timeline as the previous ones left it (SHA-012).</li>
 *     <li>Each listed leaving supply has its validTo set at its closing date on the row it had before the agreement
 *     started: its activated row with the greatest validFrom before the agreement's earliest validFrom, after the
 *     placements. The close may shorten or extend that row, up to the next start of the supply (SHA-013).</li>
 *     <li>On the agreement's first activation, every leaving supply that is not listed is closed as SHA-009 does, at
 *     the earliest validFrom of the request (SHA-013).</li>
 * </ol>
 *
 * <p>Any invalid row refuses the whole request, with every error found (SHA-015).
 *
 * <p>Pure: it reads only the rows it is given and writes nothing.
 */
public final class CoefficientDatesPlan {

    private static final Comparator<CoefficientDate> REQUEST_ORDER = Comparator
            .comparing(CoefficientDate::validFrom)
            .thenComparing(item -> item.coefficientId().toString());

    private CoefficientDatesPlan() {
    }

    /**
     * @param sharingAgreementId  the agreement whose dates are set
     * @param plantTimeline       every row of the plant, any agreement, pending or not, as it stands before the request
     * @param statuses            the status of every agreement of the plant
     * @param today               today, in the application time zone
     * @param zoneId              the application time zone
     * @param earliestOutsidePlant the earliest validFrom of the community's other plants; null when there is none
     */
    public record Input(UUID sharingAgreementId, Collection<SupplyPartitionCoefficient> plantTimeline,
                        Map<UUID, SharingAgreementStatus> statuses, LocalDate today, ZoneId zoneId,
                        CoefficientDatesRequest request, Instant earliestOutsidePlant) {
    }

    public sealed interface Result permits Planned, Refused {
    }

    /**
     * @param writes  every row whose period changes, as it must be stored
     * @param effects what the writes change
     */
    public record Planned(List<SupplyPartitionCoefficient> writes, CoefficientDatesEffects effects) implements Result {
    }

    public record Refused(List<RowError> errors) implements Result {
    }

    /**
     * One invalid row of the request. {@code coefficientId} is the requested coefficient, or the row a leaving closure
     * targets; null when a leaving closure found none.
     */
    public record RowError(CoefficientActivationErrorCode code, UUID coefficientId, UUID supplyId,
                           UUID conflictingCoefficientId) {
    }

    public static Result plan(Input input) {
        return new Computation(input).run();
    }

    private static final class Computation {

        private final Input input;
        private final Map<UUID, SupplyPartitionCoefficient> before = new LinkedHashMap<>();
        private final Map<UUID, SupplyPartitionCoefficient> projected;
        private final List<RowError> errors = new ArrayList<>();
        private final Set<UUID> placedIds = new LinkedHashSet<>();
        private final Set<UUID> stagedIds = new LinkedHashSet<>();
        private final Map<UUID, LeavingClosureSource> closureSources = new LinkedHashMap<>();

        private Computation(Input input) {
            this.input = input;
            input.plantTimeline().forEach(row -> before.put(row.getId(), row));
            this.projected = new LinkedHashMap<>(before);
        }

        private Result run() {
            boolean firstActivation = agreementRows(before).noneMatch(row -> row.getValidFrom() != null);
            Set<UUID> agreementSupplyIds = agreementRows(before)
                    .map(SupplyPartitionCoefficient::getSupplyId)
                    .collect(Collectors.toSet());

            placeCoefficients();
            closeListedSupplies(agreementSupplyIds);
            if (firstActivation && !placedIds.isEmpty()) {
                closeOmittedSupplies();
            }
            checkNoProjectedOverlap();

            if (!errors.isEmpty()) {
                return new Refused(errors);
            }
            return new Planned(stagedIds.stream().map(projected::get).toList(), effects());
        }

        private void placeCoefficients() {
            for (CoefficientDate item : input.request().coefficients().stream().sorted(REQUEST_ORDER).toList()) {
                SupplyPartitionCoefficient coefficient = projected.get(item.coefficientId());
                if (coefficient == null || !coefficient.getSharingAgreementId().equals(input.sharingAgreementId())) {
                    errors.add(new RowError(CoefficientActivationErrorCode.COEFFICIENT_NOT_IN_AGREEMENT,
                            item.coefficientId(), null, null));
                    continue;
                }
                Instant newValidFrom = startOf(item.validFrom());
                if (newValidFrom.equals(coefficient.getValidFrom())) {
                    continue;
                }
                if (item.validFrom().isAfter(input.today())) {
                    errors.add(error(CoefficientActivationErrorCode.DATE_IN_FUTURE, coefficient));
                    continue;
                }
                List<SupplyPartitionCoefficient> supplyTimeline = projected.values().stream()
                        .filter(row -> row.getSupplyId().equals(coefficient.getSupplyId()))
                        .toList();
                CoefficientPlacement.Result placement = CoefficientPlacement.place(supplyTimeline, coefficient,
                        newValidFrom);
                if (placement instanceof CoefficientPlacement.StartTaken startTaken) {
                    errors.add(overlap(coefficient, startTaken.conflictingWith()));
                } else if (placement instanceof CoefficientPlacement.NotBeforeOwnClose) {
                    errors.add(error(CoefficientActivationErrorCode.ACTIVATION_DATE_NOT_BEFORE_SUCCESSOR, coefficient));
                } else if (placement instanceof CoefficientPlacement.Placed placed) {
                    placedIds.add(coefficient.getId());
                    placed.writes().forEach(this::stage);
                }
            }
        }

        private void closeListedSupplies(Set<UUID> agreementSupplyIds) {
            Optional<Instant> agreementStart = agreementRows(projected)
                    .map(SupplyPartitionCoefficient::getValidFrom)
                    .filter(Objects::nonNull)
                    .min(Comparator.naturalOrder());
            for (LeavingClosureDate closure : input.request().leavingClosures()) {
                UUID supplyId = closure.supplyId();
                if (agreementSupplyIds.contains(supplyId)) {
                    errors.add(new RowError(CoefficientActivationErrorCode.LEAVING_SUPPLY_IN_AGREEMENT,
                            null, supplyId, null));
                    continue;
                }
                List<SupplyPartitionCoefficient> supplyRows = projected.values().stream()
                        .filter(row -> row.getSupplyId().equals(supplyId))
                        .filter(row -> row.getValidFrom() != null)
                        .toList();
                Optional<SupplyPartitionCoefficient> target = agreementStart.flatMap(start -> supplyRows.stream()
                        .filter(row -> row.getValidFrom().isBefore(start))
                        .max(Comparator.comparing(SupplyPartitionCoefficient::getValidFrom)));
                if (target.isEmpty()) {
                    errors.add(new RowError(CoefficientActivationErrorCode.NO_COEFFICIENT_TO_CLOSE,
                            null, supplyId, null));
                    continue;
                }
                SupplyPartitionCoefficient row = target.get();
                if (closure.closedOn().isAfter(input.today())) {
                    errors.add(error(CoefficientActivationErrorCode.DATE_IN_FUTURE, row));
                    continue;
                }
                Instant newValidTo = startOf(closure.closedOn());
                if (!newValidTo.isAfter(row.getValidFrom())) {
                    errors.add(error(CoefficientActivationErrorCode.CLOSURE_DATE_NOT_AFTER_ACTIVATION, row));
                    continue;
                }
                Optional<SupplyPartitionCoefficient> next = supplyRows.stream()
                        .filter(other -> other.getValidFrom().isAfter(row.getValidFrom()))
                        .min(Comparator.comparing(SupplyPartitionCoefficient::getValidFrom));
                if (next.isPresent() && newValidTo.isAfter(next.get().getValidFrom())) {
                    errors.add(overlap(row, next.get()));
                    continue;
                }
                if (newValidTo.equals(row.getValidTo())) {
                    continue;
                }
                stage(withValidTo(row, newValidTo));
                closureSources.put(row.getId(), LeavingClosureSource.REQUEST);
            }
        }

        private void closeOmittedSupplies() {
            Instant earliest = startOf(input.request().coefficients().stream()
                    .map(CoefficientDate::validFrom)
                    .min(Comparator.naturalOrder())
                    .orElseThrow());
            Set<UUID> listedSupplyIds = input.request().leavingClosures().stream()
                    .map(LeavingClosureDate::supplyId)
                    .collect(Collectors.toSet());
            for (SupplyPartitionCoefficient closed :
                    LeavingSupplyClosure.close(before.values(), input.sharingAgreementId(), earliest)) {
                if (listedSupplyIds.contains(closed.getSupplyId())) {
                    continue;
                }
                stage(closed);
                closureSources.put(closed.getId(), LeavingClosureSource.DEFAULT);
            }
        }

        /**
         * The backstop over every supply a staged row belongs to: the checks above compare a row with its neighbours,
         * this compares the projected result as a whole, as the exclusion constraint will.
         */
        private void checkNoProjectedOverlap() {
            if (stagedIds.isEmpty()) {
                return;
            }
            Set<UUID> supplyIds = stagedIds.stream()
                    .map(id -> projected.get(id).getSupplyId())
                    .collect(Collectors.toSet());
            List<SupplyPartitionCoefficient> rows = projected.values().stream()
                    .filter(row -> supplyIds.contains(row.getSupplyId()))
                    .toList();
            for (CoefficientOverlapDetector.Overlap overlap : CoefficientOverlapDetector.findOverlaps(rows)) {
                if (stagedIds.contains(overlap.first().getId())) {
                    errors.add(overlap(overlap.first(), overlap.second()));
                }
                if (stagedIds.contains(overlap.second().getId())) {
                    errors.add(overlap(overlap.second(), overlap.first()));
                }
            }
        }

        private CoefficientDatesEffects effects() {
            List<CoefficientPeriodChange> coefficients = new ArrayList<>();
            List<NeighbourChange> neighbours = new ArrayList<>();
            List<LeavingClosureChange> closures = new ArrayList<>();
            Set<UUID> agreementIds = new LinkedHashSet<>();
            for (UUID id : stagedIds) {
                SupplyPartitionCoefficient old = before.get(id);
                SupplyPartitionCoefficient now = projected.get(id);
                agreementIds.add(now.getSharingAgreementId());
                if (placedIds.contains(id)) {
                    coefficients.add(new CoefficientPeriodChange(id, now.getSupplyId(), old.getValidFrom(),
                            old.getValidTo(), now.getValidFrom(), now.getValidTo()));
                } else if (closureSources.containsKey(id)) {
                    closures.add(new LeavingClosureChange(now.getSupplyId(), id, now.getSharingAgreementId(),
                            old.getValidTo(), now.getValidTo(), closureSources.get(id)));
                } else {
                    neighbours.add(new NeighbourChange(id, now.getSupplyId(), now.getSharingAgreementId(),
                            old.getValidTo(), now.getValidTo()));
                }
            }
            List<AgreementStatusChange> agreements = agreementIds.stream()
                    .map(id -> new AgreementStatusChange(id, input.statuses().get(id),
                            projectedStatus(id, input.statuses().get(id))))
                    .toList();
            CommunityStartChange communityStart = new CommunityStartChange(
                    earliest(before.values()), earliest(projected.values()));
            return new CoefficientDatesEffects(coefficients, neighbours, closures, agreements, communityStart);
        }

        /**
         * The status {@code RecomputeSharingAgreementStatusRepository} will write: SUPERSEDED when the agreement has
         * rows and every one of them is closed, PUBLISHED otherwise. A DRAFT is never recomputed.
         */
        private SharingAgreementStatus projectedStatus(UUID agreementId, SharingAgreementStatus current) {
            if (current == SharingAgreementStatus.DRAFT) {
                return current;
            }
            List<SupplyPartitionCoefficient> rows = projected.values().stream()
                    .filter(row -> row.getSharingAgreementId().equals(agreementId))
                    .toList();
            return !rows.isEmpty() && rows.stream().allMatch(row -> row.getValidTo() != null)
                    ? SharingAgreementStatus.SUPERSEDED : SharingAgreementStatus.PUBLISHED;
        }

        private Instant earliest(Collection<SupplyPartitionCoefficient> plantRows) {
            return Stream.concat(plantRows.stream().map(SupplyPartitionCoefficient::getValidFrom),
                            Stream.of(input.earliestOutsidePlant()))
                    .filter(Objects::nonNull)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
        }

        private Stream<SupplyPartitionCoefficient> agreementRows(Map<UUID, SupplyPartitionCoefficient> rows) {
            return rows.values().stream()
                    .filter(row -> row.getSharingAgreementId().equals(input.sharingAgreementId()));
        }

        private void stage(SupplyPartitionCoefficient write) {
            projected.put(write.getId(), write);
            stagedIds.add(write.getId());
        }

        private Instant startOf(LocalDate date) {
            return date.atStartOfDay(input.zoneId()).toInstant();
        }

        private static RowError error(CoefficientActivationErrorCode code, SupplyPartitionCoefficient row) {
            return new RowError(code, row.getId(), row.getSupplyId(), null);
        }

        private static RowError overlap(SupplyPartitionCoefficient row, SupplyPartitionCoefficient conflictingWith) {
            return new RowError(CoefficientActivationErrorCode.PERIOD_OVERLAP, row.getId(), row.getSupplyId(),
                    conflictingWith.getId());
        }

        private static SupplyPartitionCoefficient withValidTo(SupplyPartitionCoefficient row, Instant validTo) {
            return new SupplyPartitionCoefficient.Builder()
                    .withId(row.getId())
                    .withSupplyId(row.getSupplyId())
                    .withPlantId(row.getPlantId())
                    .withSharingAgreementId(row.getSharingAgreementId())
                    .withCoefficient(row.getCoefficient())
                    .withValidFrom(row.getValidFrom())
                    .withValidTo(validTo)
                    .withCreatedAt(row.getCreatedAt())
                    .build();
        }
    }
}
