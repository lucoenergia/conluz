package org.lucoenergia.conluz.domain.admin.supply.sync;

import org.lucoenergia.conluz.domain.admin.supply.DatadisSupply;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepositoryDatadis;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.infrastructure.shared.time.StringToLocalDateConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DatadisSuppliesSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisSuppliesSyncService.class);

    private final GetSupplyRepository getSupplyRepository;
    private final UpdateSupplyRepository updateSupplyRepository;
    private final GetSupplyRepositoryDatadis getSupplyRepositoryDatadis;
    private final GetUserRepository getUserRepository;

    public DatadisSuppliesSyncService(GetSupplyRepository getSupplyRepository, UpdateSupplyRepository updateSupplyRepository,
                                      GetSupplyRepositoryDatadis getSupplyRepositoryDatadis, GetUserRepository getUserRepository) {
        this.getSupplyRepository = getSupplyRepository;
        this.updateSupplyRepository = updateSupplyRepository;
        this.getSupplyRepositoryDatadis = getSupplyRepositoryDatadis;
        this.getUserRepository = getUserRepository;
    }

    /**
     * Synchronizes the supplies for all users retrieving data from datadis.es
     */
    public void synchronizeSupplies() {
        // Get all users
        List<User> allUsers = getUserRepository.findAll();
        Map<String, Supply> allSupplies = getSupplyRepository.findAll().stream()
                .collect(Collectors.toMap(Supply::getCode, supply -> supply));

        for (User user : allUsers) {
            List<DatadisSupply> datadisSupplies = getSupplyRepositoryDatadis.getSuppliesByUser(user);
            for (DatadisSupply datadisSupply : datadisSupplies) {
                Supply supply = allSupplies.get(datadisSupply.getCups());
                if (supply != null) {
                    supply.setAddress(datadisSupply.getAddress());
                    supply.setDistributorCode(datadisSupply.getDistributorCode());
                    supply.setDistributor(datadisSupply.getDistributor());
                    supply.setPointType(datadisSupply.getPointType());
                    supply.setValidDateFrom(datadisSupply.getValidDateFrom() != null ?
                            StringToLocalDateConverter.convert(datadisSupply.getValidDateFrom()) :
                            null);

                    updateSupplyRepository.update(supply);
                    LOGGER.info("Supply with code {} synchronized with datadis.es.", datadisSupply.getCups());
                } else {
                    LOGGER.warn("Datadis supply with CUPS {} not found as registered supply.", datadisSupply.getCups());
                }
            }
        }
    }
}
