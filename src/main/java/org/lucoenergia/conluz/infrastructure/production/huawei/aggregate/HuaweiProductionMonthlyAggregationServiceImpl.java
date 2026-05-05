package org.lucoenergia.conluz.infrastructure.production.huawei.aggregate;

import org.lucoenergia.conluz.domain.production.get.GetEnergyStationRepository;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionMonthlyAggregationRepository;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionMonthlyAggregationService;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Month;
import java.util.List;
import java.util.Optional;

@Service
public class HuaweiProductionMonthlyAggregationServiceImpl implements HuaweiProductionMonthlyAggregationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HuaweiProductionMonthlyAggregationServiceImpl.class);

    private final GetEnergyStationRepository getEnergyStationRepository;
    private final HuaweiProductionMonthlyAggregationRepository aggregationRepository;
    private final GetHuaweiConfigRepository getHuaweiConfigRepository;

    public HuaweiProductionMonthlyAggregationServiceImpl(GetEnergyStationRepository getEnergyStationRepository,
                                                         HuaweiProductionMonthlyAggregationRepository aggregationRepository,
                                                         GetHuaweiConfigRepository getHuaweiConfigRepository) {
        this.getEnergyStationRepository = getEnergyStationRepository;
        this.aggregationRepository = aggregationRepository;
        this.getHuaweiConfigRepository = getHuaweiConfigRepository;
    }

    @Override
    public void aggregateMonthlyProductions(int year) {
        if (isDisabled()) {
            return;
        }
        List<Plant> allPlants = getEnergyStationRepository.findAll();

        for (Plant plant : allPlants) {
            for (Month month : Month.values()) {
                aggregateForPlantMonthYear(plant, month, year);
            }
        }
    }

    @Override
    public void aggregateMonthlyProductions(Month month, int year) {
        if (isDisabled()) {
            return;
        }
        List<Plant> allPlants = getEnergyStationRepository.findAll();

        for (Plant plant : allPlants) {
            aggregateForPlantMonthYear(plant, month, year);
        }
    }

    @Override
    public void aggregateMonthlyProductions(String plantCode, Month month, int year) {
        if (isDisabled()) {
            return;
        }
        Optional<Plant> plantOptional = getEnergyStationRepository.findByCode(plantCode);
        if (plantOptional.isEmpty()) {
            throw new PlantNotFoundException(plantCode);
        }

        aggregateForPlantMonthYear(plantOptional.get(), month, year);
    }

    private boolean isDisabled() {
        Optional<HuaweiConfig> config = getHuaweiConfigRepository.getHuaweiConfig();
        if (config.isEmpty()) {
            LOGGER.info("No Huawei config found. Skipping monthly aggregation.");
            return true;
        }
        if (!Boolean.TRUE.equals(config.get().getEnabled())) {
            LOGGER.info("Huawei integration is disabled. Skipping monthly aggregation.");
            return true;
        }
        return false;
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
