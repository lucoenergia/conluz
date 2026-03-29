package org.lucoenergia.conluz.domain.production.get;

import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.InverterProvider;

import java.util.List;
import java.util.Optional;

public interface GetEnergyStationRepository {

    List<Plant> findAll();

    List<Plant> findAllByInverterProvider(InverterProvider provider);

    Optional<Plant> findByCode(String code);
}
