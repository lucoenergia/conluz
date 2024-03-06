package org.lucoenergia.conluz.domain.admin.supply;

import org.lucoenergia.conluz.domain.shared.SupplyCode;

public class SupplyAlreadyExistsException extends RuntimeException {

    private final SupplyCode code;

    public SupplyAlreadyExistsException(SupplyCode code) {
        this.code = code;
    }

    public SupplyCode getUserId() {
        return code;
    }
}
