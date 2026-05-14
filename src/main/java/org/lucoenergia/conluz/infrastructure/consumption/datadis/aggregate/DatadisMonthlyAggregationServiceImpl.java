package org.lucoenergia.conluz.infrastructure.consumption.datadis.aggregate;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisMonthlyAggregationRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisMonthlyAggregationService;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Month;
import java.util.List;
import java.util.Optional;

@Service
public class DatadisMonthlyAggregationServiceImpl implements DatadisMonthlyAggregationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisMonthlyAggregationServiceImpl.class);

    private final GetSupplyRepository getSupplyRepository;
    private final DatadisMonthlyAggregationRepository aggregationRepository;

    public DatadisMonthlyAggregationServiceImpl(GetSupplyRepository getSupplyRepository,
                                                DatadisMonthlyAggregationRepository aggregationRepository) {
        this.getSupplyRepository = getSupplyRepository;
        this.aggregationRepository = aggregationRepository;
    }

    @Override
    public void aggregateMonthlyConsumptions(int year) {
        List<Supply> allSupplies = getSupplyRepository.findAll();

        for (Supply supply : allSupplies) {
            if (supply.getDistributor() == null || supply.getDistributor().getCode() == null || supply.getDistributor().getCode().isBlank()) {
                LOGGER.warn("Skipping supply with ID: {} because it does not have distributor code", supply.getId());
                continue;
            }

            for (Month month : Month.values()) {
                aggregateForSupplyMonthYear(supply, month, year);
            }
        }
    }

    @Override
    public void aggregateMonthlyConsumptions(SupplyCode supplyCode, Month month, int year) {
        Optional<Supply> supplyOptional = getSupplyRepository.findByCode(supplyCode);
        if (supplyOptional.isEmpty()) {
            throw new SupplyNotFoundException(supplyCode);
        }

        Supply supply = supplyOptional.get();
        if (supply.getDistributor() == null || supply.getDistributor().getCode() == null || supply.getDistributor().getCode().isBlank()) {
            LOGGER.warn("Skipping supply with ID: {} because it does not have distributor code", supply.getId());
            return;
        }

        aggregateForSupplyMonthYear(supply, month, year);
    }

    @Override
    public void aggregateMonthlyConsumptions(Month month, int year) {
        List<Supply> allSupplies = getSupplyRepository.findAll();

        for (Supply supply : allSupplies) {
            if (supply.getDistributor() == null || supply.getDistributor().getCode() == null || supply.getDistributor().getCode().isBlank()) {
                LOGGER.warn("Skipping supply with ID: {} because it does not have distributor code", supply.getId());
                continue;
            }

            aggregateForSupplyMonthYear(supply, month, year);
        }
    }

    private void aggregateForSupplyMonthYear(Supply supply, Month month, int year) {
        try {
            LOGGER.info("Aggregating monthly consumption for supply ID: {}, month: {}, year: {}",
                    supply.getId(), month, year);
            aggregationRepository.aggregateMonthlyConsumption(supply, month, year);
            LOGGER.info("Successfully aggregated monthly consumption for supply ID: {}, month: {}, year: {}",
                    supply.getId(), month, year);
        } catch (Exception e) {
            LOGGER.error("Failed to aggregate monthly consumption for supply ID: {}, month: {}, year: {}",
                    supply.getId(), month, year, e);
        }
    }
}
