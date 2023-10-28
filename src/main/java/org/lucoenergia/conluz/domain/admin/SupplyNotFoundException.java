package org.lucoenergia.conluz.domain.admin;

import org.lucoenergia.conluz.domain.shared.SupplyId;

public class SupplyNotFoundException extends RuntimeException {

    private final SupplyId id;

    public SupplyNotFoundException(SupplyId id) {
        this.id = id;
    }

    public SupplyId getId() {
        return id;
    }
}
