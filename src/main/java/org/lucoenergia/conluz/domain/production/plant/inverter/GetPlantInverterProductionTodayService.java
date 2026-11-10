package org.lucoenergia.conluz.domain.production.plant.inverter;

import java.util.UUID;

public interface GetPlantInverterProductionTodayService {

    /**
     * Today's inverter production of the plant, the current local day being the one of the plant's community.
     *
     * @throws org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException if the plant does not exist
     */
    PlantInverterProductionToday getToday(UUID plantId);
}
