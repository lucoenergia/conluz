package org.lucoenergia.conluz.domain.production.plant.create;

import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.shared.UserId;

public interface CreatePlantRepository {

    Plant create(Plant supply, UserId id);
}
