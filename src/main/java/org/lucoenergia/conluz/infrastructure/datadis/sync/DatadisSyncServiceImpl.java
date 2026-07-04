package org.lucoenergia.conluz.infrastructure.datadis.sync;

import org.apache.commons.lang3.StringUtils;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.persist.PersistDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.get.GetDatadisConfigRepository;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncService;
import org.lucoenergia.conluz.domain.production.datadis.DatadisProduction;
import org.lucoenergia.conluz.domain.production.datadis.PersistDatadisProductionRepository;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.infrastructure.admin.supply.DatadisSupplyConfigurationException;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisDisabledException;
import org.lucoenergia.conluz.infrastructure.production.datadis.DatadisProductionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class DatadisSyncServiceImpl implements DatadisSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisSyncServiceImpl.class);

    private final GetDatadisConsumptionRepository getDatadisConsumptionRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final PersistDatadisConsumptionRepository persistDatadisConsumptionRepository;
    private final GetPlantRepository getPlantRepository;
    private final DatadisProductionMapper datadisProductionMapper;
    private final PersistDatadisProductionRepository persistDatadisProductionRepository;
    private final GetDatadisConfigRepository getDatadisConfigRepository;

    public DatadisSyncServiceImpl(@Qualifier("getDatadisConsumptionRepositoryRest") GetDatadisConsumptionRepository getDatadisConsumptionRepository,
                                  GetSupplyRepository getSupplyRepository,
                                  PersistDatadisConsumptionRepository persistDatadisConsumptionRepository,
                                  GetPlantRepository getPlantRepository,
                                  DatadisProductionMapper datadisProductionMapper,
                                  PersistDatadisProductionRepository persistDatadisProductionRepository,
                                  GetDatadisConfigRepository getDatadisConfigRepository) {
        this.getDatadisConsumptionRepository = getDatadisConsumptionRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.persistDatadisConsumptionRepository = persistDatadisConsumptionRepository;
        this.getPlantRepository = getPlantRepository;
        this.datadisProductionMapper = datadisProductionMapper;
        this.persistDatadisProductionRepository = persistDatadisProductionRepository;
        this.getDatadisConfigRepository = getDatadisConfigRepository;
    }

    @Override
    public void synchronize(UUID communityId, LocalDate startDate, LocalDate endDate, String supplyCode) {
        Optional<DatadisConfig> config = getDatadisConfigRepository.findByCommunityId(communityId);
        if (config.isEmpty() || !Boolean.TRUE.equals(config.get().getEnabled())) {
            throw new DatadisDisabledException();
        }

        if (supplyCode != null && !supplyCode.isBlank()) {
            synchronize(communityId, startDate, endDate, SupplyCode.of(supplyCode));
        } else {
            synchronize(communityId, startDate, endDate);
        }
    }

    @Override
    public void synchronize(UUID communityId, LocalDate startDate, LocalDate endDate) {
        Set<String> plantCups = getPlantRepository.findSupplyCodesByCommunity(communityId);
        List<Supply> communitySupplies = getSupplyRepository.findAllByCommunityId(communityId);
        for (Supply supply : communitySupplies) {
            processSingleSupply(supply, startDate, endDate, plantCups);
        }
    }

    @Override
    public void synchronize(UUID communityId, LocalDate startDate, LocalDate endDate, SupplyCode supplyCode) {
        Supply supply = getSupplyRepository.findByCode(supplyCode)
                .orElseThrow(() -> new SupplyNotFoundException(supplyCode));
        if (supply.getCommunity() == null || !communityId.equals(supply.getCommunity().getId())) {
            throw new SupplyNotFoundException(supplyCode);
        }
        Set<String> plantCups = getPlantRepository.findSupplyCodesByCommunity(communityId);
        processSingleSupply(supply, startDate, endDate, plantCups);
    }

    private void processSingleSupply(Supply supply, LocalDate startDate, LocalDate endDate, Set<String> plantCups) {
        if (supply.getDistributor() == null || StringUtils.isBlank(supply.getDistributor().getCode())) {
            LOGGER.warn("Skipping supply with ID: {} because does not have distributor code", supply.getId());
            return;
        }

        LOGGER.info("Processing supply with ID: {}", supply.getId());

        boolean isPlantSupply = plantCups.contains(supply.getCode());

        LocalDate validDateFrom = startDate;

        while (validDateFrom.isBefore(endDate) || validDateFrom.isEqual(endDate)) {

            Month month = validDateFrom.getMonth();
            int year = validDateFrom.getYear();

            LOGGER.info("Processing month: {}/{}", month.getValue(), year);

            try {
                List<DatadisConsumption> consumptions = getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(supply, month, year);

                if (!consumptions.isEmpty()) {
                    if (isPlantSupply) {
                        persistProductionAndZeroedConsumption(consumptions);
                    } else {
                        persistDatadisConsumptionRepository.persistHourlyConsumptions(consumptions);
                    }
                    LOGGER.info("Hourly consumptions persisted");
                } else {
                    LOGGER.warn("Hourly consumptions are empty");
                }
            } catch (DatadisSupplyConfigurationException e) {
                LOGGER.error("Unable to retrieve hourly consumptions of supply with ID {} for month {}/{}. Error: {}", supply.getId(),
                        month.getValue(), year, e.getMessage());
            }

            validDateFrom = validDateFrom.plusMonths(1);
        }
    }

    /**
     * For a plant supply the Datadis surplus is the energy the plant produced, so it is stored as
     * production. The consumption is then persisted with its surplus zeroed out (on copies, leaving
     * the original DTOs untouched) to avoid counting the same energy as both surplus and production.
     */
    private void persistProductionAndZeroedConsumption(List<DatadisConsumption> consumptions) {
        List<DatadisProduction> productions = datadisProductionMapper.mapList(consumptions);
        persistDatadisProductionRepository.persistHourlyProductions(productions);

        List<DatadisConsumption> zeroedConsumptions = new ArrayList<>(consumptions.size());
        for (DatadisConsumption consumption : consumptions) {
            zeroedConsumptions.add(copyWithZeroSurplus(consumption));
        }
        persistDatadisConsumptionRepository.persistHourlyConsumptions(zeroedConsumptions);
    }

    private DatadisConsumption copyWithZeroSurplus(DatadisConsumption original) {
        DatadisConsumption copy = new DatadisConsumption();
        copy.setCups(original.getCups());
        copy.setDate(original.getDate());
        copy.setTime(original.getTime());
        copy.setConsumptionKWh(original.getConsumptionKWh());
        copy.setObtainMethod(original.getObtainMethod());
        copy.setSurplusEnergyKWh(0f);
        copy.setGenerationEnergyKWh(original.getGenerationEnergyKWh());
        copy.setSelfConsumptionEnergyKWh(original.getSelfConsumptionEnergyKWh());
        return copy;
    }

}
