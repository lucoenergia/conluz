package org.lucoenergia.conluz.domain.production.huawei.sync;

import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.InverterProvider;
import org.lucoenergia.conluz.domain.production.get.GetEnergyStationRepository;
import org.lucoenergia.conluz.domain.production.huawei.HourlyProduction;
import org.lucoenergia.conluz.domain.production.huawei.RealTimeProduction;
import org.lucoenergia.conluz.domain.production.huawei.persist.PersistHuaweiProductionRepository;
import org.lucoenergia.conluz.infrastructure.production.huawei.get.GetHuaweiHourlyProductionRepositoryRest;
import org.lucoenergia.conluz.infrastructure.production.huawei.get.GetHuaweiRealTimeProductionRepositoryRest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SyncHuaweiProductionService {

    private final PersistHuaweiProductionRepository persistHuaweiProductionRepository;
    private final GetHuaweiRealTimeProductionRepositoryRest getHuaweiRealTimeProductionRepositoryRest;
    private final GetHuaweiHourlyProductionRepositoryRest getHuaweiHourlyProductionRepositoryRest;
    private final GetEnergyStationRepository getEnergyStationRepository;

    public SyncHuaweiProductionService(PersistHuaweiProductionRepository persistHuaweiProductionRepository,
                                       GetHuaweiRealTimeProductionRepositoryRest getHuaweiRealTimeProductionRepositoryRest, GetHuaweiHourlyProductionRepositoryRest getHuaweiHourlyProductionRepositoryRest,
                                       GetEnergyStationRepository getEnergyStationRepository) {
        this.persistHuaweiProductionRepository = persistHuaweiProductionRepository;
        this.getHuaweiRealTimeProductionRepositoryRest = getHuaweiRealTimeProductionRepositoryRest;
        this.getHuaweiHourlyProductionRepositoryRest = getHuaweiHourlyProductionRepositoryRest;
        this.getEnergyStationRepository = getEnergyStationRepository;
    }

    /**
     * Syncs the real-time production data from Huawei energy stations.
     * It retrieves all energy stations with Huawei inverters, gets the real-time production data
     * for each station, and persists the production data.
     */
    public void syncRealTimeProduction() {

        // Get all energy stations with Huawei inverter
        List<Plant> huaweiStations = getEnergyStationRepository.findAllByInverterProvider(InverterProvider.HUAWEI);

        // Get the productions for every station
        List<RealTimeProduction> productions = getHuaweiRealTimeProductionRepositoryRest.getRealTimeProduction(huaweiStations);

        // Persist the production data
        persistHuaweiProductionRepository.persistRealTimeProduction(productions);
    }

    public void syncHourlyProduction() {

        // Get all energy stations with Huawei inverter
        List<Plant> huaweiStations = getEnergyStationRepository.findAllByInverterProvider(InverterProvider.HUAWEI);

        // Get the productions for every station
        List<HourlyProduction> productions = getHuaweiHourlyProductionRepositoryRest.getHourlyProduction(huaweiStations);

        // Persist the production data
        persistHuaweiProductionRepository.persistHourlyProduction(productions);
    }
}
