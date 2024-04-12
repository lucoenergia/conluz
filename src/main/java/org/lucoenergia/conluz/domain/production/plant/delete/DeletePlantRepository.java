package org.lucoenergia.conluz.domain.production.plant.delete;

import org.lucoenergia.conluz.domain.shared.PlantId;

public interface DeletePlantRepository {

    void delete(PlantId id);
}
