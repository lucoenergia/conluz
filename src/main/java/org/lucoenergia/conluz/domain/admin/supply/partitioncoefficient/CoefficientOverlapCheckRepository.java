package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

public interface CoefficientOverlapCheckRepository {

    /**
     * Flushes pending writes and forces the deferred no_overlapping_coefficients check to run now
     * instead of at commit. Throws {@link SupplyPartitionCoefficientOverlapException} if it fails.
     */
    void flushAndCheckNoOverlap();
}
