package org.lucoenergia.conluz.infrastructure.production.huawei.sync;

import org.lucoenergia.conluz.domain.production.get.GetEnergyStationRepository;
import org.lucoenergia.conluz.domain.production.huawei.HourlyProduction;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.RealTimeProduction;
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
import java.util.Optional;

@Service
public class SyncHuaweiProductionServiceImpl implements SyncHuaweiProductionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncHuaweiProductionServiceImpl.class);

    private final PersistHuaweiProductionRepository persistHuaweiProductionRepository;
    private final GetHuaweiRealTimeProductionRepositoryRest getHuaweiRealTimeProductionRepositoryRest;
    private final GetHuaweiHourlyProductionRepositoryRest getHuaweiHourlyProductionRepositoryRest;
    private final GetEnergyStationRepository getEnergyStationRepository;
    private final GetHuaweiConfigRepository getHuaweiConfigRepository;

    public SyncHuaweiProductionServiceImpl(PersistHuaweiProductionRepository persistHuaweiProductionRepository,
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

    @Override
    public void syncRealTimeProduction() {
        List<HuaweiConfig> enabledConfigs = getHuaweiConfigRepository.getEnabledHuaweiConfigs();
        if (enabledConfigs.isEmpty()) {
            LOGGER.info("No enabled Huawei configs found. Skipping real-time sync.");
            return;
        }

        for (HuaweiConfig config : enabledConfigs) {
            syncRealTimeProductionForPlant(config);
        }
    }

    @Override
    public void syncHourlyProduction(OffsetDateTime startDate, OffsetDateTime endDate) {
        if (startDate.isAfter(endDate)) {
            LOGGER.error("Start date is after end date. Start date: {}, end date: {}", startDate, endDate);
            return;
        }

        List<HuaweiConfig> enabledConfigs = getHuaweiConfigRepository.getEnabledHuaweiConfigs();
        if (enabledConfigs.isEmpty()) {
            LOGGER.info("No enabled Huawei configs found. Skipping hourly sync.");
            return;
        }

        for (HuaweiConfig config : enabledConfigs) {
            syncHourlyProductionForPlant(config, startDate, endDate);
        }
    }

    private void syncRealTimeProductionForPlant(HuaweiConfig config) {
        if (config.getPlantId() == null) {
            LOGGER.warn("Huawei config {} has no plant assigned, skipping.", config.getId());
            return;
        }
        Optional<Plant> plant = getEnergyStationRepository.findById(config.getPlantId());
        if (plant.isEmpty()) {
            LOGGER.warn("Plant {} not found for Huawei config {}, skipping.", config.getPlantId(), config.getId());
            return;
        }
        try {
            List<RealTimeProduction> productions = getHuaweiRealTimeProductionRepositoryRest.getRealTimeProduction(
                    List.of(plant.get()), config.getBaseUrl(), config.getUsername(), config.getPassword());
            persistHuaweiProductionRepository.persistRealTimeProduction(productions);
        } catch (Exception e) {
            LOGGER.error("Failed to sync real-time production for plant {}", config.getPlantId(), e);
        }
    }

    private void syncHourlyProductionForPlant(HuaweiConfig config, OffsetDateTime startDate, OffsetDateTime endDate) {
        if (config.getPlantId() == null) {
            LOGGER.warn("Huawei config {} has no plant assigned, skipping.", config.getId());
            return;
        }
        Optional<Plant> plant = getEnergyStationRepository.findById(config.getPlantId());
        if (plant.isEmpty()) {
            LOGGER.warn("Plant {} not found for Huawei config {}, skipping.", config.getPlantId(), config.getId());
            return;
        }
        try {
            List<HourlyProduction> productions = getHuaweiHourlyProductionRepositoryRest.getHourlyProductionByDateInterval(
                    List.of(plant.get()), startDate, endDate, config.getBaseUrl(),
                    config.getUsername(), config.getPassword());
            persistHuaweiProductionRepository.persistHourlyProduction(productions);
        } catch (Exception e) {
            LOGGER.error("Failed to sync hourly production for plant {}", config.getPlantId(), e);
        }
    }
}
