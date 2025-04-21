package org.lucoenergia.conluz.domain.shared;

import java.util.UUID;

public class SupplyPartitionId {

    private final UUID id;

    public SupplyPartitionId(UUID id) {
        this.id = id;
    }

    public static SupplyPartitionId of(UUID id) {
        return new SupplyPartitionId(id);
    }

    public UUID getId() {
        return id;
    }
}
