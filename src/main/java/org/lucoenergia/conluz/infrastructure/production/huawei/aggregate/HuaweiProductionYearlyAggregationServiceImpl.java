package org.lucoenergia.conluz.infrastructure.production.huawei.aggregate;

import org.lucoenergia.conluz.domain.production.get.GetEnergyStationRepository;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionYearlyAggregationRepository;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionYearlyAggregationService;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.infrastructure.production.huawei.HuaweiDisabledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class HuaweiProductionYearlyAggregationServiceImpl implements HuaweiProductionYearlyAggregationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HuaweiProductionYearlyAggregationServiceImpl.class);

    private final GetEnergyStationRepository getEnergyStationRepository;
    private final HuaweiProductionYearlyAggregationRepository aggregationRepository;
    private final GetHuaweiConfigRepository getHuaweiConfigRepository;
    private final GetPlantRepository getPlantRepository;

    public HuaweiProductionYearlyAggregationServiceImpl(GetEnergyStationRepository getEnergyStationRepository,
                                                        HuaweiProductionYearlyAggregationRepository aggregationRepository,
                                                        GetHuaweiConfigRepository getHuaweiConfigRepository,
                                                        GetPlantRepository getPlantRepository) {
        this.getEnergyStationRepository = getEnergyStationRepository;
        this.aggregationRepository = aggregationRepository;
        this.getHuaweiConfigRepository = getHuaweiConfigRepository;
        this.getPlantRepository = getPlantRepository;
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
    public void aggregateYearlyProductions(UUID communityId, int year) {
        for (Plant plant : communityPlants(communityId)) {
            aggregateForPlantYear(plant, year);
        }
    }

    @Override
    public void aggregateYearlyProductions(UUID communityId, String plantProviderCode, int year) {
        Plant plant = getEnergyStationRepository.findByProviderCode(plantProviderCode)
                .orElseThrow(() -> new PlantNotFoundException(plantProviderCode));
        if (!getPlantRepository.findPlantProviderCodesByCommunity(communityId).contains(plantProviderCode)) {
            throw new PlantNotFoundException(plantProviderCode);
        }
        aggregateForPlantYear(plant, year);
    }

    @Override
    public void syncYearlyProductions(UUID communityId, String plantProviderCode, int year) {
        if (getHuaweiConfigRepository.getEnabledHuaweiConfigs().isEmpty()) {
            throw new HuaweiDisabledException();
        }

        if (plantProviderCode != null && !plantProviderCode.isBlank()) {
            aggregateYearlyProductions(communityId, plantProviderCode, year);
        } else {
            aggregateYearlyProductions(communityId, year);
        }
    }

    /**
     * Resolves the plants belonging to the given community from their codes.
     */
    private List<Plant> communityPlants(UUID communityId) {
        return getPlantRepository.findPlantProviderCodesByCommunity(communityId).stream()
                .map(getEnergyStationRepository::findByProviderCode)
                .flatMap(Optional::stream)
                .toList();
    }

    private void aggregateForPlantYear(Plant plant, int year) {
        try {
            aggregationRepository.aggregateYearlyProduction(plant, year);
        } catch (Exception e) {
            LOGGER.error("Failed to aggregate yearly production for plant: {}, year: {}",
                    plant.getProviderCode(), year, e);
        }
    }
}
