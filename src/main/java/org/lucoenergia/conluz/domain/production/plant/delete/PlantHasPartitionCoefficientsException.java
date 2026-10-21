package org.lucoenergia.conluz.domain.production.plant.delete;

import org.lucoenergia.conluz.domain.shared.PlantId;

/**
 * A partition coefficient references the plant, so the plant is not deleted (PLT-001). A coefficient is part of the
 * community's record of how the plant's energy was shared.
 */
public class PlantHasPartitionCoefficientsException extends RuntimeException {

    private final PlantId plantId;

    public PlantHasPartitionCoefficientsException(PlantId plantId) {
        this.plantId = plantId;
    }

    public PlantId getPlantId() {
        return plantId;
    }
}
