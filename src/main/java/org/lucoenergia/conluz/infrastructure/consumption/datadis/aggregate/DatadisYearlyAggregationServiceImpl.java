package org.lucoenergia.conluz.infrastructure.consumption.datadis.aggregate;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisYearlyAggregationRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisYearlyAggregationService;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DatadisYearlyAggregationServiceImpl implements DatadisYearlyAggregationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisYearlyAggregationServiceImpl.class);

    private final GetSupplyRepository getSupplyRepository;
    private final DatadisYearlyAggregationRepository aggregationRepository;

    public DatadisYearlyAggregationServiceImpl(GetSupplyRepository getSupplyRepository,
                                               DatadisYearlyAggregationRepository aggregationRepository) {
        this.getSupplyRepository = getSupplyRepository;
        this.aggregationRepository = aggregationRepository;
    }

    @Override
    public void aggregateYearlyConsumptions(int year) {
        List<Supply> allSupplies = getSupplyRepository.findAll();

        for (Supply supply : allSupplies) {
            if (supply.getDistributor() == null || supply.getDistributor().getCode() == null || supply.getDistributor().getCode().isBlank()) {
                LOGGER.warn("Skipping supply with ID: {} because it does not have distributor code", supply.getId());
                continue;
            }

            aggregateForSupplyYear(supply, year);
        }
    }

    @Override
    public void aggregateYearlyConsumptions(SupplyCode supplyCode, int year) {
        Optional<Supply> supplyOptional = getSupplyRepository.findByCode(supplyCode);
        if (supplyOptional.isEmpty()) {
            throw new SupplyNotFoundException(supplyCode);
        }

        Supply supply = supplyOptional.get();
        if (supply.getDistributor() == null || supply.getDistributor().getCode() == null || supply.getDistributor().getCode().isBlank()) {
            LOGGER.warn("Skipping supply with ID: {} because it does not have distributor code", supply.getId());
            return;
        }

        aggregateForSupplyYear(supply, year);
    }

    private void aggregateForSupplyYear(Supply supply, int year) {
        try {
            LOGGER.info("Aggregating yearly consumption for supply ID: {}, year: {}",
                    supply.getId(), year);
            aggregationRepository.aggregateYearlyConsumption(supply, year);
            LOGGER.info("Successfully aggregated yearly consumption for supply ID: {}, year: {}",
                    supply.getId(), year);
        } catch (Exception e) {
            LOGGER.error("Failed to aggregate yearly consumption for supply ID: {}, year: {}",
                    supply.getId(), year, e);
        }
    }
}
