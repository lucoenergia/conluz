package org.lucoenergia.conluz.domain.production.plant;

import java.util.UUID;

public class PlantMissingRegulatoryCodeException extends RuntimeException {

    private final UUID plantId;

    public PlantMissingRegulatoryCodeException(UUID plantId) {
        this.plantId = plantId;
    }

    public UUID getPlantId() {
        return plantId;
    }
}
