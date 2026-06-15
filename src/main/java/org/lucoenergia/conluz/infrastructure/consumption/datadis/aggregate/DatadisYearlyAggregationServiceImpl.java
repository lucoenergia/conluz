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
import java.util.UUID;

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
    public void aggregateYearlyConsumptions(UUID communityId, int year) {
        for (Supply supply : getSupplyRepository.findAllByCommunityId(communityId)) {
            if (hasNoDistributorCode(supply)) {
                continue;
            }
            aggregateForSupplyYear(supply, year);
        }
    }

    @Override
    public void aggregateYearlyConsumptions(UUID communityId, SupplyCode supplyCode, int year) {
        Supply supply = getSupplyRepository.findByCode(supplyCode)
                .orElseThrow(() -> new SupplyNotFoundException(supplyCode));
        if (supply.getCommunity() == null || !communityId.equals(supply.getCommunity().getId())) {
            throw new SupplyNotFoundException(supplyCode);
        }
        if (hasNoDistributorCode(supply)) {
            return;
        }
        aggregateForSupplyYear(supply, year);
    }

    private boolean hasNoDistributorCode(Supply supply) {
        return supply.getDistributor() == null || supply.getDistributor().getCode() == null
                || supply.getDistributor().getCode().isBlank();
    }

    private void aggregateForSupplyYear(Supply supply, int year) {
        try {
            LOGGER.debug("Aggregating yearly consumption for supply ID: {}, year: {}",
                    supply.getId(), year);
            aggregationRepository.aggregateYearlyConsumption(supply, year);
            LOGGER.debug("Successfully aggregated yearly consumption for supply ID: {}, year: {}",
                    supply.getId(), year);
        } catch (Exception e) {
            LOGGER.error("Failed to aggregate yearly consumption for supply ID: {}, year: {}",
                    supply.getId(), year, e);
        }
    }
}
