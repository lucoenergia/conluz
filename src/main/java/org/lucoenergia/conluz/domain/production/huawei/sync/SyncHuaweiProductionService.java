package org.lucoenergia.conluz.domain.production.huawei.sync;

import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.InverterProvider;
import org.lucoenergia.conluz.domain.production.get.GetEnergyStationRepository;
import org.lucoenergia.conluz.domain.production.huawei.HourlyProduction;
import org.lucoenergia.conluz.domain.production.huawei.RealTimeProduction;
import org.lucoenergia.conluz.domain.production.huawei.persist.PersistHuaweiProductionRepository;
import org.lucoenergia.conluz.infrastructure.production.huawei.get.GetHuaweiHourlyProductionRepositoryRest;
import org.lucoenergia.conluz.infrastructure.production.huawei.get.GetHuaweiRealTimeProductionRepositoryRest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SyncHuaweiProductionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncHuaweiProductionService.class);

    private final PersistHuaweiProductionRepository persistHuaweiProductionRepository;
    private final GetHuaweiRealTimeProductionRepositoryRest getHuaweiRealTimeProductionRepositoryRest;
    private final GetHuaweiHourlyProductionRepositoryRest getHuaweiHourlyProductionRepositoryRest;
    private final GetEnergyStationRepository getEnergyStationRepository;
    private final GetHuaweiConfigRepository getHuaweiConfigRepository;

    public SyncHuaweiProductionService(PersistHuaweiProductionRepository persistHuaweiProductionRepository,
                                       GetHuaweiRealTimeProductionRepositoryRest getHuaweiRealTimeProductionRepositoryRest,
                                       GetHuaweiHourlyProductionRepositoryRest getHuaweiHourlyProductionRepositoryRest,
                                       GetEnergyStationRepository getEnergyStationRepository,
                                       GetHuaweiConfigRepository getHuaweiConfigRepository) {
        this.persistHuaweiProductionRepository = persistHuaweiProductionRepository;
        this.getHuaweiRealTimeProductionRepositoryRest = getHuaweiRealTimeProductionRepositoryRest;
        this.getHuaweiHourlyProductionRepositoryRest = getHuaweiHourlyProductionRepositoryRest;
        this.getEnergyStationRepository = getEnergyStationRepository;
        this.getHuaweiConfigRepository = getHuaweiConfigRepository;
    }

    /**
     * Syncs the real-time production data from Huawei energy stations.
     * It retrieves all energy stations with Huawei inverters, gets the real-time production data
     * for each station, and persists the production data.
     */
    public void syncRealTimeProduction() {

        // Get Huawei configuration
        Optional<HuaweiConfig> huaweiConfig = getHuaweiConfigRepository.getHuaweiConfig();
        if (huaweiConfig.isEmpty()) {
            LOGGER.info("No Huawei config found");
            return;
        }

        // Get all energy stations with Huawei inverter
        List<Plant> huaweiStations = getEnergyStationRepository.findAllByInverterProvider(InverterProvider.HUAWEI);
        if (huaweiStations.isEmpty()) {
            LOGGER.info("No Huawei stations found");
            return;
        }

        // Get the productions for every station
        List<RealTimeProduction> productions = getHuaweiRealTimeProductionRepositoryRest.getRealTimeProduction(huaweiStations);

        // Persist the production data
        persistHuaweiProductionRepository.persistRealTimeProduction(productions);
    }

    public void syncHourlyProduction() {

        // Get Huawei configuration
        Optional<HuaweiConfig> huaweiConfig = getHuaweiConfigRepository.getHuaweiConfig();
        if (huaweiConfig.isEmpty()) {
            LOGGER.info("No Huawei config found");
            return;
        }

        // Get all energy stations with Huawei inverter
        List<Plant> huaweiStations = getEnergyStationRepository.findAllByInverterProvider(InverterProvider.HUAWEI);
        if (huaweiStations.isEmpty()) {
            LOGGER.info("No Huawei stations found");
            return;
        }

        // Get the productions for every station
        List<HourlyProduction> productions = getHuaweiHourlyProductionRepositoryRest.getHourlyProduction(huaweiStations);

        // Persist the production data
        persistHuaweiProductionRepository.persistHourlyProduction(productions);
    }
}
