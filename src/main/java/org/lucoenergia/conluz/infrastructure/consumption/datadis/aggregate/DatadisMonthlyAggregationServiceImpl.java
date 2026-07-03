package org.lucoenergia.conluz.infrastructure.consumption.datadis.aggregate;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisMonthlyAggregationRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisMonthlyAggregationService;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.get.GetDatadisConfigRepository;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisDisabledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DatadisMonthlyAggregationServiceImpl implements DatadisMonthlyAggregationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisMonthlyAggregationServiceImpl.class);

    private final GetSupplyRepository getSupplyRepository;
    private final DatadisMonthlyAggregationRepository aggregationRepository;
    private final GetDatadisConfigRepository getDatadisConfigRepository;

    public DatadisMonthlyAggregationServiceImpl(GetSupplyRepository getSupplyRepository,
                                                DatadisMonthlyAggregationRepository aggregationRepository,
                                                GetDatadisConfigRepository getDatadisConfigRepository) {
        this.getSupplyRepository = getSupplyRepository;
        this.aggregationRepository = aggregationRepository;
        this.getDatadisConfigRepository = getDatadisConfigRepository;
    }

    @Override
    public void syncMonthlyConsumptions(UUID communityId, String supplyCode, Integer month, int year) {
        Optional<DatadisConfig> config = getDatadisConfigRepository.findByCommunityId(communityId);
        if (config.isEmpty() || !Boolean.TRUE.equals(config.get().getEnabled())) {
            throw new DatadisDisabledException();
        }

        if (supplyCode != null && !supplyCode.isBlank()) {
            if (month != null) {
                aggregateMonthlyConsumptions(communityId, SupplyCode.of(supplyCode), Month.of(month), year);
            } else {
                for (Month everyMonth : Month.values()) {
                    aggregateMonthlyConsumptions(communityId, SupplyCode.of(supplyCode), everyMonth, year);
                }
            }
        } else {
            if (month != null) {
                aggregateMonthlyConsumptions(communityId, Month.of(month), year);
            } else {
                aggregateMonthlyConsumptions(communityId, year);
            }
        }
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

    @Override
    public void aggregateMonthlyConsumptions(UUID communityId, int year) {
        for (Supply supply : getSupplyRepository.findAllByCommunityId(communityId)) {
            if (hasNoDistributorCode(supply)) {
                continue;
            }
            for (Month month : Month.values()) {
                aggregateForSupplyMonthYear(supply, month, year);
            }
        }
    }

    @Override
    public void aggregateMonthlyConsumptions(UUID communityId, Month month, int year) {
        for (Supply supply : getSupplyRepository.findAllByCommunityId(communityId)) {
            if (hasNoDistributorCode(supply)) {
                continue;
            }
            aggregateForSupplyMonthYear(supply, month, year);
        }
    }

    @Override
    public void aggregateMonthlyConsumptions(UUID communityId, SupplyCode supplyCode, Month month, int year) {
        Supply supply = getSupplyRepository.findByCode(supplyCode)
                .orElseThrow(() -> new SupplyNotFoundException(supplyCode));
        if (supply.getCommunity() == null || !communityId.equals(supply.getCommunity().getId())) {
            throw new SupplyNotFoundException(supplyCode);
        }
        if (hasNoDistributorCode(supply)) {
            return;
        }
        aggregateForSupplyMonthYear(supply, month, year);
    }

    private boolean hasNoDistributorCode(Supply supply) {
        return supply.getDistributor() == null || supply.getDistributor().getCode() == null
                || supply.getDistributor().getCode().isBlank();
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
