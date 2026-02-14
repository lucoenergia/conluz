package org.lucoenergia.conluz.infrastructure.consumption.datadis.sync;

import org.apache.commons.lang3.StringUtils;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.persist.PersistDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.sync.DatadisConsumptionSyncService;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.infrastructure.admin.supply.DatadisSupplyConfigurationException;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Month;
import java.util.*;

@Service
public class DatadisConsumptionSyncServiceImpl implements DatadisConsumptionSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisConsumptionSyncServiceImpl.class);

    private final GetDatadisConsumptionRepository getDatadisConsumptionRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final PersistDatadisConsumptionRepository persistDatadisConsumptionRepository;
    private final DateConverter dateConverter;

    public DatadisConsumptionSyncServiceImpl(@Qualifier("getDatadisConsumptionRepositoryRest") GetDatadisConsumptionRepository getDatadisConsumptionRepository,
                                             GetSupplyRepository getSupplyRepository,
                                             PersistDatadisConsumptionRepository persistDatadisConsumptionRepository,
                                             DateConverter dateConverter) {
        this.getDatadisConsumptionRepository = getDatadisConsumptionRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.persistDatadisConsumptionRepository = persistDatadisConsumptionRepository;
        this.dateConverter = dateConverter;
    }

    @Override
    public void synchronizeConsumptions(LocalDate startDate, LocalDate endDate) {
        List<Supply> allSupplies = getSupplyRepository.findAll();
        for (Supply supply : allSupplies) {
            processSingleSupply(supply, startDate, endDate);
        }
    }

    @Override
    public void synchronizeConsumptions(LocalDate startDate, LocalDate endDate, SupplyCode supplyCode) {
        Optional<Supply> supplyOptional = getSupplyRepository.findByCode(supplyCode);
        if (supplyOptional.isEmpty()) {
            throw new SupplyNotFoundException(supplyCode);
        }
        processSingleSupply(supplyOptional.get(), startDate, endDate);
    }

    private void processSingleSupply(Supply supply, LocalDate startDate, LocalDate endDate) {
        if (StringUtils.isBlank(supply.getDistributorCode())) {
            LOGGER.warn("Skipping supply with ID: {} because does not have distributor code", supply.getId());
            return;
        }

        LOGGER.info("Processing supply with ID: {}", supply.getId());

        LocalDate validDateFrom = startDate;
        List<DatadisConsumption> aggregatedMonthlyConsumptions = new ArrayList<>();

        while (validDateFrom.isBefore(endDate) || validDateFrom.isEqual(endDate)) {

            Month month = validDateFrom.getMonth();
            int year = validDateFrom.getYear();

            LOGGER.info("Processing month: {}/{}", month.getValue(), year);

            try {
                List<DatadisConsumption> consumptions = getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(supply, month, year);

                if (!consumptions.isEmpty()) {
                    persistDatadisConsumptionRepository.persistHourlyConsumptions(consumptions);
                    LOGGER.info("Hourly consumptions persisted");
                    // Calculate aggregated monthly consumption
                    DatadisConsumption aggregatedMonthlyConsumption = calculateMonthlyAggregatedConsumption(consumptions);
                    aggregatedMonthlyConsumptions.add(aggregatedMonthlyConsumption);
                } else {
                    LOGGER.warn("Hourly consumptions are empty");
                }
            } catch (DatadisSupplyConfigurationException e) {
                LOGGER.error("Unable to retrieve hourly consumptions of supply with ID {} for month {}/{}. Error: {}", supply.getId(),
                        month.getValue(), year, e.getMessage());
            }

            validDateFrom = validDateFrom.plusMonths(1);
        }

        // Persist monthly consumption
        if (!aggregatedMonthlyConsumptions.isEmpty()) {
            persistDatadisConsumptionRepository.persistMonthlyConsumptions(aggregatedMonthlyConsumptions);
            LOGGER.info("Monthly consumptions persisted");

            // Calculate and persist yearly consumption
            List<DatadisConsumption> aggregatedYearlyConsumption = calculateYearlyAggregatedConsumption(aggregatedMonthlyConsumptions);
            if (!aggregatedYearlyConsumption.isEmpty()) {
                persistDatadisConsumptionRepository.persistYearlyConsumptions(aggregatedYearlyConsumption);
                LOGGER.info("Yearly consumptions persisted");
            } else {
                LOGGER.warn("Yearly consumptions are empty");
            }

        } else {
            LOGGER.warn("Monthly consumptions are empty");
        }
    }

    private DatadisConsumption calculateMonthlyAggregatedConsumption(List<DatadisConsumption> consumptions) {
        DatadisConsumption aggregated = new DatadisConsumption();

        Float totalConsumption = consumptions.stream()
                .map(DatadisConsumption::getConsumptionKWh)
                .filter(Objects::nonNull)
                .reduce(0f, Float::sum);

        Float totalSurplus = consumptions.stream()
                .map(DatadisConsumption::getSurplusEnergyKWh)
                .filter(Objects::nonNull)
                .reduce(0f, Float::sum);

        Float totalGeneration = consumptions.stream()
                .map(DatadisConsumption::getGenerationEnergyKWh)
                .filter(Objects::nonNull)
                .reduce(0f, Float::sum);

        Float totalSelfConsumption = consumptions.stream()
                .map(DatadisConsumption::getSelfConsumptionEnergyKWh)
                .filter(Objects::nonNull)
                .reduce(0f, Float::sum);

        DatadisConsumption firstConsumption = consumptions.get(0);
        aggregated.setCups(firstConsumption.getCups());
        aggregated.setDate(firstConsumption.getDate());
        aggregated.setTime(firstConsumption.getTime());
        aggregated.setObtainMethod(firstConsumption.getObtainMethod());
        aggregated.setConsumptionKWh(totalConsumption);
        aggregated.setSurplusEnergyKWh(totalSurplus);
        aggregated.setGenerationEnergyKWh(totalGeneration);
        aggregated.setSelfConsumptionEnergyKWh(totalSelfConsumption);

        return aggregated;
    }

    private List<DatadisConsumption> calculateYearlyAggregatedConsumption(List<DatadisConsumption> consumptions) {
        List<DatadisConsumption> yearsConsumptions = new ArrayList<>();

        DatadisConsumption firstConsumption = consumptions.get(0);
        String cups = firstConsumption.getCups();
        String obtainMethod = firstConsumption.getObtainMethod();

        Map<Integer, Float> consumptionByYear = new HashMap<>();
        Map<Integer, Float> surplusByYear = new HashMap<>();
        Map<Integer, Float> generationByYear = new HashMap<>();
        Map<Integer, Float> selfConsumptionByYear = new HashMap<>();

        for (DatadisConsumption monthConsumption : consumptions) {

            int year = dateConverter.getYearFromStringDate(monthConsumption.getDate());

            // Consumption
            consumptionByYear.merge(year, monthConsumption.getConsumptionKWh(), Float::sum);

            // Surplus
            surplusByYear.merge(year, monthConsumption.getSurplusEnergyKWh(), Float::sum);

            // Generation
            generationByYear.merge(year, monthConsumption.getGenerationEnergyKWh(), Float::sum);

            // Self consumption
            selfConsumptionByYear.merge(year, monthConsumption.getSelfConsumptionEnergyKWh(), Float::sum);
        }

        for (int year : consumptionByYear.keySet()) {

            DatadisConsumption yearConsumption = new DatadisConsumption();
            yearConsumption.setCups(cups);
            yearConsumption.setDate(year + "/12/31");
            yearConsumption.setTime("00:00");
            yearConsumption.setObtainMethod(obtainMethod);
            yearConsumption.setConsumptionKWh(consumptionByYear.getOrDefault(year, 0.0f));
            yearConsumption.setSurplusEnergyKWh(surplusByYear.getOrDefault(year, 0.0f));
            yearConsumption.setGenerationEnergyKWh(generationByYear.getOrDefault(year, 0.0f));
            yearConsumption.setSelfConsumptionEnergyKWh(selfConsumptionByYear.getOrDefault(year, 0.0f));

            yearsConsumptions.add(yearConsumption);
        }

        return yearsConsumptions;
    }
}
