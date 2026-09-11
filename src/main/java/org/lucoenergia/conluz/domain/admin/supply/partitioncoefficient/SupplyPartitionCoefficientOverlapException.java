package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

/**
 * Raised when persisting supply-partition-coefficient period changes would overlap another
 * period for the same (plantId, supplyId), detected via the no_overlapping_coefficients
 * exclusion constraint. Reached only for what CoefficientOverlapDetector's pre-write check cannot
 * see: a concurrent request committing between that check and this write, or pre-existing
 * inconsistent data. Never a single well-formed request, so the message does not imply a date the
 * user entered wrong.
 */
public class SupplyPartitionCoefficientOverlapException extends RuntimeException {
    public SupplyPartitionCoefficientOverlapException(Throwable cause) {
        super(cause);
    }
}
