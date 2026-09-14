package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import java.util.Optional;

/**
 * Whether a coefficient's {@code validTo} was written automatically by the activation cascade
 * (see {@code CoefficientActivationServiceImpl.setValidFrom}, which closes a predecessor row when
 * activating its successor) rather than authored explicitly via a close/baja
 * ({@code CoefficientActivationServiceImpl.setValidTo}).
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
