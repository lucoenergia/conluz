package org.lucoenergia.conluz.domain.admin.supply.delete;

import org.lucoenergia.conluz.domain.shared.SupplyId;

/**
 * A plant is attached to the supply, so the supply is not deleted (SUP-001). A plant carries the community's
 * production and its sharing agreements: it is removed deliberately, never as a side effect of deleting its supply.
 */
public class SupplyHasPlantsException extends RuntimeException {

    private final SupplyId supplyId;

    public SupplyHasPlantsException(SupplyId supplyId) {
        this.supplyId = supplyId;
    }

    public SupplyId getSupplyId() {
        return supplyId;
    }
}
