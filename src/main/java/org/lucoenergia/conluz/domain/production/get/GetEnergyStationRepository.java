package org.lucoenergia.conluz.domain.production.get;

import org.lucoenergia.conluz.domain.production.EnergyStation;
import org.lucoenergia.conluz.domain.production.InverterProvider;

import java.util.List;

public interface GetEnergyStationRepository {

    List<EnergyStation> findAll();

    List<EnergyStation> findAllByInverterProvider(InverterProvider provider);
}
