package org.lucoenergia.conluz.infrastructure.production.huawei.aggregate;

import org.lucoenergia.conluz.domain.production.get.GetEnergyStationRepository;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionMonthlyAggregationRepository;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionMonthlyAggregationService;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class HuaweiProductionMonthlyAggregationServiceImpl implements HuaweiProductionMonthlyAggregationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HuaweiProductionMonthlyAggregationServiceImpl.class);

    private final GetEnergyStationRepository getEnergyStationRepository;
    private final HuaweiProductionMonthlyAggregationRepository aggregationRepository;
    private final GetHuaweiConfigRepository getHuaweiConfigRepository;
    private final GetPlantRepository getPlantRepository;

    public HuaweiProductionMonthlyAggregationServiceImpl(GetEnergyStationRepository getEnergyStationRepository,
                                                         HuaweiProductionMonthlyAggregationRepository aggregationRepository,
                                                         GetHuaweiConfigRepository getHuaweiConfigRepository,
                                                         GetPlantRepository getPlantRepository) {
        this.getEnergyStationRepository = getEnergyStationRepository;
        this.aggregationRepository = aggregationRepository;
        this.getHuaweiConfigRepository = getHuaweiConfigRepository;
        this.getPlantRepository = getPlantRepository;
    }

    @Override
    public void aggregateMonthlyProductions(Month month, int year) {
        List<HuaweiConfig> enabledConfigs = getHuaweiConfigRepository.getEnabledHuaweiConfigs();
        if (enabledConfigs.isEmpty()) {
            LOGGER.debug("No enabled Huawei configs found. Skipping monthly production aggregation.");
            return;
        }
        for (HuaweiConfig config : enabledConfigs) {
            Optional<Plant> plant = config.getPlantId() != null
                    ? getEnergyStationRepository.findById(config.getPlantId())
                    : Optional.empty();
            plant.ifPresentOrElse(
                    p -> aggregateForPlantMonthYear(p, month, year),
                    () -> LOGGER.warn("Plant not found for Huawei config with plantId={}", config.getPlantId())
            );
        }
    }

    @Override
    public void aggregateMonthlyProductions(UUID communityId, int year) {
        for (Plant plant : communityPlants(communityId)) {
            for (Month month : Month.values()) {
                aggregateForPlantMonthYear(plant, month, year);
            }
        }
    }

    @Override
    public void aggregateMonthlyProductions(UUID communityId, Month month, int year) {
        for (Plant plant : communityPlants(communityId)) {
            aggregateForPlantMonthYear(plant, month, year);
        }
    }

    @Override
    public void aggregateMonthlyProductions(UUID communityId, String plantCode, Month month, int year) {
        Plant plant = getEnergyStationRepository.findByCode(plantCode)
                .orElseThrow(() -> new PlantNotFoundException(plantCode));
        if (!getPlantRepository.findPlantCodesByCommunity(communityId).contains(plantCode)) {
            throw new PlantNotFoundException(plantCode);
        }
        aggregateForPlantMonthYear(plant, month, year);
    }

    /**
     * Resolves the plants belonging to the given community from their codes.
     */
    private List<Plant> communityPlants(UUID communityId) {
        return getPlantRepository.findPlantCodesByCommunity(communityId).stream()
                .map(getEnergyStationRepository::findByCode)
                .flatMap(Optional::stream)
                .toList();
    }

    private void aggregateForPlantMonthYear(Plant plant, Month month, int year) {
        try {
            aggregationRepository.aggregateMonthlyProduction(plant, month, year);
        } catch (Exception e) {
            LOGGER.error("Failed to aggregate monthly production for plant: {}, month: {}, year: {}",
                    plant.getCode(), month, year, e);
        }
    }
}
