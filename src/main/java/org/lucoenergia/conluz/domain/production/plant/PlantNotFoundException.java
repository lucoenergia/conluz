package org.lucoenergia.conluz.domain.production.plant;

import org.lucoenergia.conluz.domain.shared.PlantId;

public class PlantNotFoundException extends RuntimeException {

    private PlantId id;
    private String code;

    public PlantNotFoundException(PlantId id) {
        this.id = id;
    }

    public PlantNotFoundException(String code) {
        this.code = code;
    }

    public PlantId getId() {
        return id;
    }

    public String getCode() {
        return code;
    }
}
