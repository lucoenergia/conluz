package org.lucoenergia.conluz.domain.admin.supply.delete;

import org.lucoenergia.conluz.domain.shared.SupplyId;

/**
 * A sharing agreement holds a partition coefficient for the supply, so the supply is not deleted (SUP-001). The
 * coefficients are the content of the agreement, and deleting the supply would change an agreement as a side effect.
 */
public class SupplyHasPartitionCoefficientsException extends RuntimeException {

    private final SupplyId supplyId;

    public SupplyHasPartitionCoefficientsException(SupplyId supplyId) {
        this.supplyId = supplyId;
    }

    public SupplyId getSupplyId() {
        return supplyId;
    }
}
