package org.lucoenergia.conluz.domain.shared;

import java.util.UUID;

public class SupplyId {

    private final UUID id;

    private SupplyId(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public static SupplyId of(UUID id) {
        return new SupplyId(id);
    }
}
