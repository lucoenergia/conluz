package org.lucoenergia.conluz.infrastructure.production.huawei.sync;

import org.lucoenergia.conluz.domain.production.InverterProvider;
import org.lucoenergia.conluz.domain.production.get.GetEnergyStationRepository;
import org.lucoenergia.conluz.domain.production.huawei.HourlyProduction;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.RealTimeProduction;
import org.lucoenergia.conluz.domain.production.huawei.config.GetHuaweiConfigurationService;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.production.huawei.persist.PersistHuaweiProductionRepository;
import org.lucoenergia.conluz.domain.production.huawei.sync.SyncHuaweiProductionService;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.infrastructure.production.huawei.get.GetHuaweiHourlyProductionRepositoryRest;
import org.lucoenergia.conluz.infrastructure.production.huawei.get.GetHuaweiRealTimeProductionRepositoryRest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class SyncHuaweiProductionServiceImpl implements SyncHuaweiProductionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncHuaweiProductionServiceImpl.class);

    private final PersistHuaweiProductionRepository persistHuaweiProductionRepository;
    private final GetHuaweiRealTimeProductionRepositoryRest getHuaweiRealTimeProductionRepositoryRest;
    private final GetHuaweiHourlyProductionRepositoryRest getHuaweiHourlyProductionRepositoryRest;
    private final GetEnergyStationRepository getEnergyStationRepository;
    private final GetHuaweiConfigRepository getHuaweiConfigRepository;
    private final GetHuaweiConfigurationService getHuaweiConfigurationService;

    public SyncHuaweiProductionServiceImpl(PersistHuaweiProductionRepository persistHuaweiProductionRepository,
                                           GetHuaweiRealTimeProductionRepositoryRest getHuaweiRealTimeProductionRepositoryRest,
                                           GetHuaweiHourlyProductionRepositoryRest getHuaweiHourlyProductionRepositoryRest,
                                           GetEnergyStationRepository getEnergyStationRepository,
                                           GetHuaweiConfigRepository getHuaweiConfigRepository, GetHuaweiConfigurationService getHuaweiConfigurationService) {
        this.persistHuaweiProductionRepository = persistHuaweiProductionRepository;
        this.getHuaweiRealTimeProductionRepositoryRest = getHuaweiRealTimeProductionRepositoryRest;
        this.getHuaweiHourlyProductionRepositoryRest = getHuaweiHourlyProductionRepositoryRest;
        this.getEnergyStationRepository = getEnergyStationRepository;
        this.getHuaweiConfigRepository = getHuaweiConfigRepository;
        this.getHuaweiConfigurationService = getHuaweiConfigurationService;
    }

    /**
     * Syncs the real-time production data from Huawei energy stations.
     * It retrieves all energy stations with Huawei inverters, gets the real-time production data
     * for each station, and persists the production data.
     */
    @Override
    public void syncRealTimeProduction() {

        if (getHuaweiConfigurationService.isDisabled()) {
            LOGGER.info("Huawei integration is disabled. Skipping real-time sync.");
            return;
        }
        HuaweiConfig config = getHuaweiConfigRepository.getHuaweiConfig().get();

        // Get all energy stations with Huawei inverter
        List<Plant> huaweiStations = getEnergyStationRepository.findAllByInverterProvider(InverterProvider.HUAWEI);
        if (huaweiStations.isEmpty()) {
            LOGGER.info("No Huawei stations found.");
            return;
        }

        // Get the productions for every station
        List<RealTimeProduction> productions = getHuaweiRealTimeProductionRepositoryRest.getRealTimeProduction(
                huaweiStations, config.getBaseUrl());

        // Persist the production data
        persistHuaweiProductionRepository.persistRealTimeProduction(productions);
    }

    @Override
    public void syncHourlyProduction(OffsetDateTime startDate, OffsetDateTime endDate) {

        if (getHuaweiConfigurationService.isDisabled()) {
            LOGGER.info("Huawei integration is disabled. Skipping real-time sync.");
            return;
        }
        HuaweiConfig config = getHuaweiConfigRepository.getHuaweiConfig().get();

        if (startDate.isAfter(endDate)) {
            LOGGER.error("Start date is after end date. Start date: {}, end date: {}", startDate, endDate);
            return;
        }

        // Get all energy stations with Huawei inverter
        List<Plant> huaweiStations = getEnergyStationRepository.findAllByInverterProvider(InverterProvider.HUAWEI);
        if (huaweiStations.isEmpty()) {
            LOGGER.info("No Huawei stations found.");
            return;
        }

        // Get the productions for every station
        List<HourlyProduction> productions = getHuaweiHourlyProductionRepositoryRest.getHourlyProductionByDateInterval(
                huaweiStations, startDate, endDate, config.getBaseUrl());

        // Persist the production data
        persistHuaweiProductionRepository.persistHourlyProduction(productions);
    }
}
