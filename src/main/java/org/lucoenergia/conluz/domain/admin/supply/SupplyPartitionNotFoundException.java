package org.lucoenergia.conluz.domain.admin.supply;

public class SupplyPartitionNotFoundException extends RuntimeException {

    private SupplyPartitionId id;

    public SupplyPartitionNotFoundException(SupplyPartitionId id) {
        this.id = id;
    }

    public SupplyPartitionId getId() {
        return id;
    }
}
