package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

/**
 * Whether a coefficient has been applied by the distributor yet.
 */
public enum CoefficientApplicationState {

    /**
     * {@code validFrom} is null: materialised but not yet activated, contributes 0 until applied.
     */
    PENDING,

    /**
     * {@code validFrom} is set.
     */
    APPLIED
}
