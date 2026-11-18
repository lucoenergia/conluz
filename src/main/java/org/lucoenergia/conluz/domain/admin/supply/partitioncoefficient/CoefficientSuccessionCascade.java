package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import java.util.Optional;

/**
 * Whether a coefficient's {@code validTo} was written automatically by the activation cascade
 * (see {@link CoefficientPlacement}, which closes the previous row when a coefficient is placed after
 * it) rather than authored explicitly via a close/baja
 * ({@code CoefficientActivationServiceImpl.setValidTo}).
 *
 * <p>Nothing records which of the two wrote a {@code validTo}: it is inferred from adjacency only, so
 * an authored close that happens to equal the next row's start is indistinguishable from a
 * cascade-derived one.
 */
public final class CoefficientSuccessionCascade {

    private CoefficientSuccessionCascade() {
    }

    /**
     * {@code coefficient}'s current {@code validTo} is cascade-derived when {@code nextActivated} --
     * the nearest later activated coefficient for the same {@code (plantId, supplyId)} -- starts
     * exactly where {@code coefficient} ends. An authored close is not required to be adjacent to
     * any later row, so any other case (including no later row at all) is not cascade-derived.
     * Callers must only invoke this when {@code coefficient.getValidTo()} is non-null.
     */
    public static boolean isValidToCascadeDerived(SupplyPartitionCoefficient coefficient,
                                                    Optional<SupplyPartitionCoefficient> nextActivated) {
        return nextActivated.isPresent()
                && nextActivated.get().getValidFrom().equals(coefficient.getValidTo());
    }
}
