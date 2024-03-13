package org.lucoenergia.conluz.domain.admin.supply.sync;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSupplyRepository;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.lucoenergia.conluz.infrastructure.shared.time.StringToLocalDateConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DatadisSuppliesSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisSuppliesSyncService.class);

    private final GetSupplyRepository getSupplyRepository;
    private final UpdateSupplyRepository updateSupplyRepository;
    private final GetSupplyRepositoryDatadis getSupplyRepositoryDatadis;

    public DatadisSuppliesSyncService(GetSupplyRepository getSupplyRepository, UpdateSupplyRepository updateSupplyRepository,
                                      GetSupplyRepositoryDatadis getSupplyRepositoryDatadis) {
        this.getSupplyRepository = getSupplyRepository;
        this.updateSupplyRepository = updateSupplyRepository;
        this.getSupplyRepositoryDatadis = getSupplyRepositoryDatadis;
    }

    /**
     * Synchronizes the consumptions for all supplies.
     * It retrieves all supplies from the repository, and for each supply it retrieves the monthly consumptions
     * based on the validity date of the supply. The method iterates through the validity dates until it reaches
     * the current date, retrieving the monthly consumptions for each month and year.
     */
    public void synchronizeSupplies() {
        // Get all supplies
        long total = getSupplyRepository.count();
        PagedResult<Supply> suppliesPageResult = getSupplyRepository.findAll(
                PagedRequest.of(0, Long.valueOf(total).intValue())
        );

        Map<String, DatadisSupply> datadisSuppliesMap = getSupplyRepositoryDatadis.getSupplies().stream()
                .collect(Collectors.toMap(DatadisSupply::getCups, datadisSupply -> datadisSupply));

        for (Supply supply : suppliesPageResult.getItems()) {

            DatadisSupply datadisSupply = datadisSuppliesMap.get(supply.getCode());
            if (datadisSupply == null) {
                LOGGER.debug("Supply with code {} not found in datadis.es", supply.getCode());
                continue;
            }

            supply.setAddress(datadisSupply.getAddress());
            supply.setDistributorCode(datadisSupply.getDistributorCode());
            supply.setDistributor(datadisSupply.getDistributor());
            supply.setPointType(datadisSupply.getPointType());
            supply.setValidDateFrom(datadisSupply.getValidDateFrom() != null ?
                    StringToLocalDateConverter.convert(datadisSupply.getValidDateFrom()) :
                    null);

            updateSupplyRepository.update(supply);
        }
    }
}
