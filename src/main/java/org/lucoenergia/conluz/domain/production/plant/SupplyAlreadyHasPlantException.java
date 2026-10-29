package org.lucoenergia.conluz.domain.production.plant;

import org.lucoenergia.conluz.domain.shared.SupplyId;

/**
 * The supply is already the supply of another plant, so no further plant is put on it (PLT-002).
 */
public class SupplyAlreadyHasPlantException extends RuntimeException {

    private final SupplyId supplyId;

    public SupplyAlreadyHasPlantException(SupplyId supplyId) {
        this.supplyId = supplyId;
    }

    public SupplyAlreadyHasPlantException(SupplyId supplyId, Throwable cause) {
        super(cause);
        this.supplyId = supplyId;
    }

    public SupplyId getSupplyId() {
        return supplyId;
    }
}
