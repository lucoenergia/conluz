package org.lucoenergia.conluz.domain.production.plant.create;


import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.SupplyId;

public interface CreatePlantService {

    Plant create(Plant plant, SupplyId id);

    Plant create(Plant plant, SupplyCode code);
}
