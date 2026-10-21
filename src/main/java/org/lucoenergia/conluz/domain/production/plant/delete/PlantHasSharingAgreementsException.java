package org.lucoenergia.conluz.domain.production.plant.delete;

import org.lucoenergia.conluz.domain.shared.PlantId;

/**
 * A sharing agreement, in any status, references the plant, so the plant is not deleted (PLT-001). An agreement is
 * the community's record of how the plant's energy was shared, with its distributor files.
 */
public class PlantHasSharingAgreementsException extends RuntimeException {

    private final PlantId plantId;

    public PlantHasSharingAgreementsException(PlantId plantId) {
        this.plantId = plantId;
    }

    public PlantId getPlantId() {
        return plantId;
    }
}
