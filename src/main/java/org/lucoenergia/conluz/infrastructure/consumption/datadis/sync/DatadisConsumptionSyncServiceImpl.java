package org.lucoenergia.conluz.infrastructure.consumption.datadis.sync;

import org.apache.commons.lang3.StringUtils;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.persist.PersistDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.sync.DatadisConsumptionSyncService;
import org.lucoenergia.conluz.infrastructure.admin.supply.DatadisSupplyConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class DatadisConsumptionSyncServiceImpl implements DatadisConsumptionSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisConsumptionSyncServiceImpl.class);

    private final GetDatadisConsumptionRepository getDatadisConsumptionRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final PersistDatadisConsumptionRepository persistDatadisConsumptionRepository;

    public DatadisConsumptionSyncServiceImpl(@Qualifier("getDatadisConsumptionRepositoryRest") GetDatadisConsumptionRepository getDatadisConsumptionRepository,
                                             GetSupplyRepository getSupplyRepository,
                                             PersistDatadisConsumptionRepository persistDatadisConsumptionRepository) {
        this.getDatadisConsumptionRepository = getDatadisConsumptionRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.persistDatadisConsumptionRepository = persistDatadisConsumptionRepository;
    }

    @Override
    public void synchronizeConsumptions(LocalDate startDate, LocalDate endDate) {

        // Get all supplies
        List<Supply> allSupplies = getSupplyRepository.findAll();

        for (Supply supply : allSupplies) {

            if (StringUtils.isBlank(supply.getDistributorCode())) {
                LOGGER.warn("Skipping supply with ID: {} because does not have distributor code", supply.getId());
                continue;
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
            } else {
                LOGGER.warn("Monthly consumptions are empty");
            }

            // Calculate and persist yearly consumption
            DatadisConsumption aggregatedYearlyConsumption = calculateMonthlyAggregatedConsumption(aggregatedMonthlyConsumptions);
            if (!aggregatedYearlyConsumption.isEmpty()) {
                persistDatadisConsumptionRepository.persistYearlyConsumptions(List.of(aggregatedYearlyConsumption));
                LOGGER.info("Yearly consumptions persisted");
            } else {
                LOGGER.warn("Yearly consumptions are empty");
            }
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
}
