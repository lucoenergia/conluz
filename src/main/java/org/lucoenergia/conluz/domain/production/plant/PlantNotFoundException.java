package org.lucoenergia.conluz.domain.production.plant;

import org.lucoenergia.conluz.domain.shared.PlantId;

public class PlantNotFoundException extends RuntimeException {

    private final PlantId id;

    public PlantNotFoundException(PlantId id) {
        this.id = id;
    }

    public PlantId getId() {
        return id;
    }
}
