package org.lucoenergia.conluz.domain.consumption.datadis.sync;

import org.apache.commons.lang3.StringUtils;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.persist.PersistDatadisConsumptionRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.DatadisSupplyConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

@Service
public class DatadisConsumptionSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisConsumptionSyncService.class);

    private final GetDatadisConsumptionRepository getDatadisConsumptionRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final PersistDatadisConsumptionRepository persistDatadisConsumptionRepository;

    public DatadisConsumptionSyncService(@Qualifier("getDatadisConsumptionRepositoryRest") GetDatadisConsumptionRepository getDatadisConsumptionRepository,
                                         GetSupplyRepository getSupplyRepository,
                                         PersistDatadisConsumptionRepository persistDatadisConsumptionRepository) {
        this.getDatadisConsumptionRepository = getDatadisConsumptionRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.persistDatadisConsumptionRepository = persistDatadisConsumptionRepository;
    }

    /**
     * Synchronizes the consumptions for all supplies.
     * It retrieves all supplies from the repository, and for each supply it retrieves the monthly consumptions
     * based on the validity date of the supply. The method iterates through the validity dates until it reaches
     * the current date, retrieving the monthly consumptions for each month and year.
     */
    public void synchronizeConsumptions() {

        // Get all supplies
        List<Supply> allSupplies = getSupplyRepository.findAll();

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate oneYearAgo = today.minusYears(1);

        for (Supply supply : allSupplies) {

            if (StringUtils.isBlank(supply.getDatadisDistributorCode())) {
                LOGGER.warn("Skipping supply with ID: {} because does not have distributor code", supply.getId());
                continue;
            }

            LOGGER.info("Processing supply with ID: {}", supply.getId());

            // Get validity date
            LocalDate validDateFrom = supply.getDatadisValidDateFrom();

            // If the valid date is not present, we set the valid date on yesterday
            if (validDateFrom == null) {
                validDateFrom = yesterday;
            }
            // If the valid date is older than a year, we set the valid date to one year ago
            if (validDateFrom.isBefore(oneYearAgo)) {
                validDateFrom = oneYearAgo;
            }

            while (validDateFrom.isBefore(today)) {

                Month month = validDateFrom.getMonth();
                int year = validDateFrom.getYear();

                LOGGER.info("Processing month: {}/{}", month.getValue(), year);

                try {
                    List<DatadisConsumption> consumptions = getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(supply, month, year);
                    if (!consumptions.isEmpty()) {
                        persistDatadisConsumptionRepository.persistConsumptions(consumptions);
                    }
                } catch (DatadisSupplyConfigurationException e) {
                    LOGGER.error("Unable to retrieve consumptions of supply with ID {} for month {}/{}. Error: {}", supply.getId(),
                            month.getValue(), year, e.getMessage());
                }

                validDateFrom = validDateFrom.plusMonths(1);
            }
        }
    }
}
