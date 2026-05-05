package org.lucoenergia.conluz.infrastructure.production.huawei.aggregate;

import org.lucoenergia.conluz.domain.production.get.GetEnergyStationRepository;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionYearlyAggregationRepository;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionYearlyAggregationService;
import org.lucoenergia.conluz.domain.production.huawei.config.GetHuaweiConfigurationService;
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
    private final GetHuaweiConfigurationService getHuaweiConfigurationService;

    public HuaweiProductionYearlyAggregationServiceImpl(GetEnergyStationRepository getEnergyStationRepository,
                                                        HuaweiProductionYearlyAggregationRepository aggregationRepository,
                                                        GetHuaweiConfigurationService getHuaweiConfigurationService) {
        this.getEnergyStationRepository = getEnergyStationRepository;
        this.aggregationRepository = aggregationRepository;
        this.getHuaweiConfigurationService = getHuaweiConfigurationService;
    }

    @Override
    public void aggregateYearlyProductions(int year) {
        if (getHuaweiConfigurationService.isDisabled()) {
            LOGGER.debug("Huawei configuration is disabled. Skipping yearly production aggregation.");
            return;
        }
        List<Plant> allPlants = getEnergyStationRepository.findAll();

        for (Plant plant : allPlants) {
            aggregateForPlantYear(plant, year);
        }
    }

    @Override
    public void aggregateYearlyProductions(String plantCode, int year) {
        if (getHuaweiConfigurationService.isDisabled()) {
            LOGGER.debug("Huawei configuration is disabled. Skipping yearly production aggregation.");
            return;
        }
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
