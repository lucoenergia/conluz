package org.lucoenergia.conluz.infrastructure.production.huawei.aggregate;

import org.lucoenergia.conluz.domain.production.get.GetEnergyStationRepository;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionYearlyAggregationRepository;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionYearlyAggregationService;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class HuaweiProductionYearlyAggregationServiceImpl implements HuaweiProductionYearlyAggregationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HuaweiProductionYearlyAggregationServiceImpl.class);

    private final GetEnergyStationRepository getEnergyStationRepository;
    private final HuaweiProductionYearlyAggregationRepository aggregationRepository;
    private final GetHuaweiConfigRepository getHuaweiConfigRepository;

    public HuaweiProductionYearlyAggregationServiceImpl(GetEnergyStationRepository getEnergyStationRepository,
                                                        HuaweiProductionYearlyAggregationRepository aggregationRepository,
                                                        GetHuaweiConfigRepository getHuaweiConfigRepository) {
        this.getEnergyStationRepository = getEnergyStationRepository;
        this.aggregationRepository = aggregationRepository;
        this.getHuaweiConfigRepository = getHuaweiConfigRepository;
    }

    @Override
    public void aggregateYearlyProductions(int year) {
        List<HuaweiConfig> enabledConfigs = getHuaweiConfigRepository.getEnabledHuaweiConfigs();
        if (enabledConfigs.isEmpty()) {
            LOGGER.debug("No enabled Huawei configs found. Skipping yearly production aggregation.");
            return;
        }
        for (HuaweiConfig config : enabledConfigs) {
            Optional<Plant> plant = config.getPlantId() != null
                    ? getEnergyStationRepository.findById(config.getPlantId())
                    : Optional.empty();
            plant.ifPresentOrElse(
                    p -> aggregateForPlantYear(p, year),
                    () -> LOGGER.warn("Plant not found for Huawei config with plantId={}", config.getPlantId())
            );
        }
    }

    @Override
    public void aggregateYearlyProductions(String plantCode, int year) {
        Optional<Plant> plantOptional = getEnergyStationRepository.findByCode(plantCode);
        if (plantOptional.isEmpty()) {
            throw new PlantNotFoundException(plantCode);
        }

        aggregateForPlantYear(plantOptional.get(), year);
    }

    private void aggregateForPlantYear(Plant plant, int year) {
        try {
            aggregationRepository.aggregateYearlyProduction(plant, year);
        } catch (Exception e) {
            LOGGER.error("Failed to aggregate yearly production for plant: {}, year: {}",
                    plant.getCode(), year, e);
        }
    }
}
