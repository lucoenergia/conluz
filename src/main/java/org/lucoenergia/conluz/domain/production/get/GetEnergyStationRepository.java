package org.lucoenergia.conluz.domain.production.get;

import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.InverterProvider;

import java.util.List;

public interface GetEnergyStationRepository {

    List<Plant> findAll();

    List<Plant> findAllByInverterProvider(InverterProvider provider);
}
