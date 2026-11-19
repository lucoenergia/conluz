package org.lucoenergia.conluz.domain.production.sharingagreement.get;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientEndState;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientSuccessionCascade;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.production.sharingagreement.RegisteredOrder;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * How each coefficient of one sharing agreement ends, derived from the agreements and coefficients of its plant,
 * already loaded. Nothing here reads a store.
 *
 * <p>Since SHA-003 an agreement created later may hold an earlier period, so creation order says nothing about
 * which coefficient succeeds which. An applied coefficient is placed by its validity (SHA-016); a pending one, which
 * has no validity, by the registered order of its agreement (SHA-017). A stored validTo is CLOSED when it was
 * authored and DERIVED when the cascade wrote it ({@link CoefficientSuccessionCascade}).</p>
 *
 * <p>DRAFT agreements other than the one read are ignored: a draft in progress never changes how an existing
 * agreement's coefficients are shown.</p>
 */
public final class CoefficientEndStateDerivation {

    /**
     * @param endDate the first instant the coefficient no longer applies; null unless the state is DERIVED or CLOSED
     */
    public record EndOfCoverage(CoefficientEndState state, Instant endDate) {
    }

    private final Map<UUID, List<SupplyPartitionCoefficient>> rowsBySupply;
    private final Map<UUID, List<SupplyPartitionCoefficient>> rowsByOtherAgreement;
    private final List<UUID> laterInRegisteredOrder;

    /**
     * @param agreement          the agreement whose coefficients are derived, in any status
     * @param plantAgreements    every agreement of its plant, in any status and order
     * @param plantCoefficients  every coefficient of its plant, in any agreement, pending or not
     * @param zone               the zone a stored start is read as a local day in, for the registered order
     */
    public CoefficientEndStateDerivation(SharingAgreement agreement, Collection<SharingAgreement> plantAgreements,
                                         Collection<SupplyPartitionCoefficient> plantCoefficients, ZoneId zone) {
        List<SharingAgreement> others = plantAgreements.stream()
                .filter(other -> other.getStatus() != SharingAgreementStatus.DRAFT
                        && !other.getId().equals(agreement.getId()))
                .toList();
        List<UUID> registered = RegisteredOrder.sort(
                        Stream.concat(others.stream(), Stream.of(agreement)).toList(),
                        SharingAgreement::getId, SharingAgreement::getDistributorFileSentOn,
                        SharingAgreement::getCreatedAt,
                        RegisteredOrder.earliestValidFrom(plantCoefficients,
                                SupplyPartitionCoefficient::getSharingAgreementId,
                                SupplyPartitionCoefficient::getValidFrom, zone))
                .stream()
                .map(SharingAgreement::getId)
                .toList();
        this.laterInRegisteredOrder = registered.subList(registered.indexOf(agreement.getId()) + 1, registered.size());

        this.rowsBySupply = plantCoefficients.stream()
                .collect(Collectors.groupingBy(SupplyPartitionCoefficient::getSupplyId));
        Set<UUID> otherIds = others.stream().map(SharingAgreement::getId).collect(Collectors.toSet());
        this.rowsByOtherAgreement = plantCoefficients.stream()
                .filter(row -> otherIds.contains(row.getSharingAgreementId()))
                .collect(Collectors.groupingBy(SupplyPartitionCoefficient::getSharingAgreementId));
    }

    public EndOfCoverage derive(SupplyPartitionCoefficient coefficient) {
        Optional<SupplyPartitionCoefficient> nextActivated = nextActivatedAfter(coefficient);
        if (coefficient.getValidTo() != null) {
            CoefficientEndState state = CoefficientSuccessionCascade.isValidToCascadeDerived(coefficient, nextActivated)
                    ? CoefficientEndState.DERIVED : CoefficientEndState.CLOSED;
            return new EndOfCoverage(state, coefficient.getValidTo());
        }
        return coefficient.getValidFrom() != null
                ? applied(coefficient, nextActivated)
                : pending(coefficient);
    }

    // SHA-016: "later" means later in validity, never in creation.
    private EndOfCoverage applied(SupplyPartitionCoefficient coefficient,
                                  Optional<SupplyPartitionCoefficient> nextActivated) {
        if (nextActivated.isPresent()) {
            // Unreachable through this application's own writes: placing a coefficient after an open one closes it
            // (SHA-003), and the no_overlapping_coefficients exclusion constraint refuses the overlap. Kept for the
            // enum's contract.
            return new EndOfCoverage(CoefficientEndState.DERIVED, nextActivated.get().getValidFrom());
        }
        boolean leftWithoutClose = rowsByOtherAgreement.values().stream()
                .anyMatch(rows -> rows.stream().anyMatch(row -> row.getValidFrom() != null
                                && row.getValidFrom().isAfter(coefficient.getValidFrom()))
                        && rows.stream().noneMatch(row -> row.getSupplyId().equals(coefficient.getSupplyId())));
        if (leftWithoutClose) {
            return new EndOfCoverage(CoefficientEndState.OPEN_ORPHAN, null);
        }
        boolean pendingSuccessor = laterInRegisteredOrder.stream()
                .flatMap(agreementId -> rowsOf(agreementId, coefficient.getSupplyId()).stream())
                .anyMatch(row -> row.getValidFrom() == null);
        return new EndOfCoverage(pendingSuccessor ? CoefficientEndState.PENDING_SUCCESSION : CoefficientEndState.OPEN,
                null);
    }

    // SHA-017: a pending coefficient has no validity, so "later" means later in registered order.
    private EndOfCoverage pending(SupplyPartitionCoefficient coefficient) {
        if (laterInRegisteredOrder.isEmpty()) {
            return new EndOfCoverage(CoefficientEndState.OPEN, null);
        }
        List<SupplyPartitionCoefficient> next = rowsOf(laterInRegisteredOrder.get(0), coefficient.getSupplyId());
        if (next.isEmpty()) {
            return new EndOfCoverage(CoefficientEndState.OPEN_ORPHAN, null);
        }
        return next.stream()
                .map(SupplyPartitionCoefficient::getValidFrom)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .map(start -> new EndOfCoverage(CoefficientEndState.DERIVED, start))
                .orElseGet(() -> new EndOfCoverage(CoefficientEndState.PENDING_SUCCESSION, null));
    }

    // The nearest activated row of the same supply starting after this one, in any agreement.
    private Optional<SupplyPartitionCoefficient> nextActivatedAfter(SupplyPartitionCoefficient coefficient) {
        if (coefficient.getValidFrom() == null) {
            return Optional.empty();
        }
        return rowsBySupply.getOrDefault(coefficient.getSupplyId(), List.of()).stream()
                .filter(row -> !row.getId().equals(coefficient.getId())
                        && row.getValidFrom() != null
                        && row.getValidFrom().isAfter(coefficient.getValidFrom()))
                .min(Comparator.comparing(SupplyPartitionCoefficient::getValidFrom));
    }

    private List<SupplyPartitionCoefficient> rowsOf(UUID agreementId, UUID supplyId) {
        return rowsByOtherAgreement.getOrDefault(agreementId, List.of()).stream()
                .filter(row -> row.getSupplyId().equals(supplyId))
                .toList();
    }
}
