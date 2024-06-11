package org.lucoenergia.conluz.domain.admin.supply;

import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.SupplyId;

public class SupplyNotFoundException extends RuntimeException {

    private SupplyId id;
    private SupplyCode code;

    public SupplyNotFoundException(SupplyId id) {
        this.id = id;
    }

    public SupplyNotFoundException(SupplyCode code) {
        this.code = code;
    }

    public SupplyId getId() {
        return id;
    }

    public SupplyCode getCode() {
        return code;
    }
}
