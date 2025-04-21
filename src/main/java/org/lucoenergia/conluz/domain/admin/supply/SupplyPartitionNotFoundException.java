package org.lucoenergia.conluz.domain.admin.supply;

import org.lucoenergia.conluz.domain.shared.SupplyPartitionId;

public class SupplyPartitionNotFoundException extends RuntimeException {

    private SupplyPartitionId id;

    public SupplyPartitionNotFoundException(SupplyPartitionId id) {
        this.id = id;
    }

    public SupplyPartitionId getId() {
        return id;
    }
}
