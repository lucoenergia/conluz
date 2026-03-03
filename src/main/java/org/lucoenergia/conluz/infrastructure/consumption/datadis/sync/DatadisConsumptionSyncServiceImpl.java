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
import java.util.List;
import java.util.Optional;

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

        while (validDateFrom.isBefore(endDate) || validDateFrom.isEqual(endDate)) {

            Month month = validDateFrom.getMonth();
            int year = validDateFrom.getYear();

            LOGGER.info("Processing month: {}/{}", month.getValue(), year);

            try {
                List<DatadisConsumption> consumptions = getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(supply, month, year);

                if (!consumptions.isEmpty()) {
                    persistDatadisConsumptionRepository.persistHourlyConsumptions(consumptions);
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

}
