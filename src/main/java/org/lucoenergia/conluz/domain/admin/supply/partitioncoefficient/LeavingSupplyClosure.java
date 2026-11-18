package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Closes, at an agreement's first activation date D, the coefficient in force at D of every supply of the plant that
 * has no coefficient in that agreement (SHA-009): a supply that leaves the distribution must stop receiving
 * attributed production.
 *
 * <p>A coefficient is in force at D when it started before D and has not ended by D: {@code validFrom < D} and
 * {@code validTo} null or after D. Its validTo becomes D, so a closure only ever shortens a period, and a row starting
 * at or after D is never touched (SHA-010).
 *
 * <p>Only the first activation closes: once any coefficient of the agreement has a validFrom, later activations and
 * corrections close nothing. A supply holding a coefficient of the agreement, pending or not, is not leaving.
 *
 * <p>Pure: it reads only the rows it is given and writes nothing.
 */
public final class LeavingSupplyClosure {

    private LeavingSupplyClosure() {
    }

    /**
     * @param plantTimeline      every row of the plant, any agreement, pending or not, as it stands before the
     *                           activation
     * @param sharingAgreementId the agreement being activated
     * @param activatedAt        D, the validFrom the activation sets; never null
     * @return the rows in force at D of the supplies absent from the agreement, with validTo set to D; empty when the
     * activation is not the agreement's first
     */
    public static List<SupplyPartitionCoefficient> close(Collection<SupplyPartitionCoefficient> plantTimeline,
                                                         UUID sharingAgreementId, Instant activatedAt) {
        Objects.requireNonNull(activatedAt, "activatedAt");
        List<SupplyPartitionCoefficient> agreementRows = plantTimeline.stream()
                .filter(row -> row.getSharingAgreementId().equals(sharingAgreementId))
                .toList();
        if (agreementRows.stream().anyMatch(row -> row.getValidFrom() != null)) {
            return List.of();
        }
        Set<UUID> agreementSupplyIds = agreementRows.stream()
                .map(SupplyPartitionCoefficient::getSupplyId)
                .collect(Collectors.toSet());

        return plantTimeline.stream()
                .filter(row -> !agreementSupplyIds.contains(row.getSupplyId()))
                .filter(row -> isInForceAt(row, activatedAt))
                .map(row -> closedAt(row, activatedAt))
                .toList();
    }

    private static boolean isInForceAt(SupplyPartitionCoefficient row, Instant instant) {
        return row.getValidFrom() != null
                && row.getValidFrom().isBefore(instant)
                && (row.getValidTo() == null || row.getValidTo().isAfter(instant));
    }

    private static SupplyPartitionCoefficient closedAt(SupplyPartitionCoefficient row, Instant validTo) {
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
