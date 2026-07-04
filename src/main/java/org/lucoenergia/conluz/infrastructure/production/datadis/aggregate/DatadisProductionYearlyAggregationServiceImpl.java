package org.lucoenergia.conluz.infrastructure.production.datadis.aggregate;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.get.GetDatadisConfigRepository;
import org.lucoenergia.conluz.domain.production.datadis.aggregate.DatadisProductionYearlyAggregationRepository;
import org.lucoenergia.conluz.domain.production.datadis.aggregate.DatadisProductionYearlyAggregationService;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisDisabledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DatadisProductionYearlyAggregationServiceImpl implements DatadisProductionYearlyAggregationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisProductionYearlyAggregationServiceImpl.class);

    private final GetSupplyRepository getSupplyRepository;
    private final DatadisProductionYearlyAggregationRepository aggregationRepository;
    private final GetDatadisConfigRepository getDatadisConfigRepository;

    public DatadisProductionYearlyAggregationServiceImpl(GetSupplyRepository getSupplyRepository,
                                                        DatadisProductionYearlyAggregationRepository aggregationRepository,
                                                        GetDatadisConfigRepository getDatadisConfigRepository) {
        this.getSupplyRepository = getSupplyRepository;
        this.aggregationRepository = aggregationRepository;
        this.getDatadisConfigRepository = getDatadisConfigRepository;
    }

    @Override
    public void syncYearlyProductions(UUID communityId, String supplyCode, int year) {
        Optional<DatadisConfig> config = getDatadisConfigRepository.findByCommunityId(communityId);
        if (config.isEmpty() || !Boolean.TRUE.equals(config.get().getEnabled())) {
            throw new DatadisDisabledException();
        }

        if (supplyCode != null && !supplyCode.isBlank()) {
            aggregateYearlyProductions(communityId, SupplyCode.of(supplyCode), year);
        } else {
            aggregateYearlyProductions(communityId, year);
        }
    }

    @Override
    public void aggregateYearlyProductions(int year) {
        List<Supply> allSupplies = getSupplyRepository.findAll();

        for (Supply supply : allSupplies) {
            if (hasNoDistributorCode(supply)) {
                LOGGER.warn("Skipping supply with ID: {} because it does not have distributor code", supply.getId());
                continue;
            }

            aggregateForSupplyYear(supply, year);
        }
    }

    @Override
    public void aggregateYearlyProductions(UUID communityId, int year) {
        for (Supply supply : getSupplyRepository.findAllByCommunityId(communityId)) {
            if (hasNoDistributorCode(supply)) {
                continue;
            }
            aggregateForSupplyYear(supply, year);
        }
    }

    @Override
    public void aggregateYearlyProductions(UUID communityId, SupplyCode supplyCode, int year) {
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
            LOGGER.debug("Aggregating yearly production for supply ID: {}, year: {}",
                    supply.getId(), year);
            aggregationRepository.aggregateYearlyProduction(supply, year);
            LOGGER.debug("Successfully aggregated yearly production for supply ID: {}, year: {}",
                    supply.getId(), year);
        } catch (Exception e) {
            LOGGER.error("Failed to aggregate yearly production for supply ID: {}, year: {}",
                    supply.getId(), year, e);
        }
    }
}
