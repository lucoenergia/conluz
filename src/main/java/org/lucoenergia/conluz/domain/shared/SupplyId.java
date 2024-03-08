package org.lucoenergia.conluz.domain.shared;

import java.util.UUID;

public class SupplyId {

    private final UUID id;

    public SupplyId(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
