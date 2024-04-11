package org.lucoenergia.conluz.domain.production.plant;

import org.lucoenergia.conluz.domain.shared.PlantCode;

public class PlantAlreadyExistsException extends RuntimeException {

    private final PlantCode code;

    public PlantAlreadyExistsException(PlantCode code) {
        this.code = code;
    }

    public PlantCode getCode() {
        return code;
    }
}
