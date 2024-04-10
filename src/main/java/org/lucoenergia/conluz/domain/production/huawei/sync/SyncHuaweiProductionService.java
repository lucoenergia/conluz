package org.lucoenergia.conluz.domain.production.huawei.sync;

import org.lucoenergia.conluz.domain.production.EnergyStation;
import org.lucoenergia.conluz.domain.production.InverterProvider;
import org.lucoenergia.conluz.domain.production.get.GetEnergyStationRepository;
import org.lucoenergia.conluz.domain.production.huawei.RealTimeProduction;
import org.lucoenergia.conluz.domain.production.huawei.persist.PersistHuaweiProductionRepository;
import org.lucoenergia.conluz.infrastructure.production.get.GetHuaweiProductionRepositoryRest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SyncHuaweiProductionService {

    private final PersistHuaweiProductionRepository persistHuaweiProductionRepository;
    private final GetHuaweiProductionRepositoryRest getHuaweiProductionRepositoryRest;
    private final GetEnergyStationRepository getEnergyStationRepository;

    public SyncHuaweiProductionService(PersistHuaweiProductionRepository persistHuaweiProductionRepository,
                                       GetHuaweiProductionRepositoryRest getHuaweiProductionRepositoryRest,
                                       GetEnergyStationRepository getEnergyStationRepository) {
        this.persistHuaweiProductionRepository = persistHuaweiProductionRepository;
        this.getHuaweiProductionRepositoryRest = getHuaweiProductionRepositoryRest;
        this.getEnergyStationRepository = getEnergyStationRepository;
    }

    /**
     * Syncs the real-time production data from Huawei energy stations.
     * It retrieves all energy stations with Huawei inverters, gets the real-time production data
     * for each station, and persists the production data.
     */
    public void syncRealTimeProduction() {

        // Get all energy stations with Huawei inverter
        List<EnergyStation> huaweiStations = getEnergyStationRepository.findAllByInverterProvider(InverterProvider.HUAWEI);

        // Get the productions for every station
        List<RealTimeProduction> productions = getHuaweiProductionRepositoryRest.getRealTimeProduction(huaweiStations);

        // Persist the production data
        persistHuaweiProductionRepository.persistRealTimeProduction(productions);
    }
}
