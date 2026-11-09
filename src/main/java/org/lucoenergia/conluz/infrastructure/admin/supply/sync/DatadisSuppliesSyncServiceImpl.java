package org.lucoenergia.conluz.infrastructure.admin.supply.sync;

import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.supply.DatadisSupply;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.contract.SupplyContract;
import org.lucoenergia.conluz.domain.admin.supply.distributor.SupplyDistributor;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepositoryDatadis;
import org.lucoenergia.conluz.domain.admin.supply.sync.DatadisSuppliesSyncService;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.get.GetDatadisConfigRepository;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisAuthorizer;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisDisabledException;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Transactional
@Service
public class DatadisSuppliesSyncServiceImpl implements DatadisSuppliesSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisSuppliesSyncServiceImpl.class);

    private final GetSupplyRepository getSupplyRepository;
    private final UpdateSupplyRepository updateSupplyRepository;
    private final GetSupplyRepositoryDatadis getSupplyRepositoryDatadis;
    private final GetDatadisConfigRepository getDatadisConfigRepository;
    private final DatadisAuthorizer datadisAuthorizer;
    private final DateConverter dateConverter;

    public DatadisSuppliesSyncServiceImpl(GetSupplyRepository getSupplyRepository, UpdateSupplyRepository updateSupplyRepository,
                                          GetSupplyRepositoryDatadis getSupplyRepositoryDatadis,
                                          GetDatadisConfigRepository getDatadisConfigRepository,
                                          DatadisAuthorizer datadisAuthorizer, DateConverter dateConverter) {
        this.getSupplyRepository = getSupplyRepository;
        this.updateSupplyRepository = updateSupplyRepository;
        this.getSupplyRepositoryDatadis = getSupplyRepositoryDatadis;
        this.getDatadisConfigRepository = getDatadisConfigRepository;
        this.datadisAuthorizer = datadisAuthorizer;
        this.dateConverter = dateConverter;
    }

    /**
     * Synchronizes the supplies of every community that has any, each with its own Datadis configuration.
     */
    public void synchronizeSupplies() {
        List<UUID> communityIds = getSupplyRepository.findAll().stream()
                .map(Supply::getCommunity)
                .filter(Objects::nonNull)
                .map(Community::getId)
                .distinct()
                .toList();
        for (UUID communityId : communityIds) {
            synchronizeSupplies(communityId);
        }
    }

    @Override
    public void synchronizeSupplies(UUID communityId) {
        DatadisConfig config = getDatadisConfigRepository.findByCommunityId(communityId)
                .filter(found -> Boolean.TRUE.equals(found.getEnabled()))
                .orElseThrow(DatadisDisabledException::new);

        List<Supply> communitySupplies = getSupplyRepository.findAllByCommunityId(communityId);
        Map<String, Supply> suppliesByCode = communitySupplies.stream()
                .collect(Collectors.toMap(Supply::getCode, supply -> supply));
        // Only the owners of this community's supplies are processed, and only this community's
        // supplies can be updated, so the job never writes another community's supplies.
        List<User> users = communitySupplies.stream()
                .map(Supply::getUser)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a))
                .values().stream()
                .toList();

        // One login per community and run: every owner is requested with the same token
        String bearerToken = "Bearer " + datadisAuthorizer.getAuthToken(config);

        processUsers(config, bearerToken, users, suppliesByCode);
    }

    private void processUsers(DatadisConfig config, String bearerToken, List<User> users,
                              Map<String, Supply> suppliesByCode) {
        for (User user : users) {

            LOGGER.info("Processing users with ID {}", user.getId());

            List<DatadisSupply> datadisSupplies = getSupplyRepositoryDatadis.getSuppliesByUser(config, bearerToken, user);
            for (DatadisSupply datadisSupply : datadisSupplies) {
                Supply supply = suppliesByCode.get(datadisSupply.getCups());
                if (supply != null) {
                    supply.setAddress(datadisSupply.getAddress());

                    SupplyDistributor distributor = new SupplyDistributor.Builder()
                            .withCode(datadisSupply.getDistributorCode())
                            .withName(datadisSupply.getDistributor())
                            .withPointType(datadisSupply.getPointType())
                            .build();
                    supply.setDistributor(distributor);

                    SupplyContract contract = new SupplyContract.Builder()
                            .withValidDateFrom(datadisSupply.getValidDateFrom() != null ?
                                    dateConverter.convertStringToLocalDate(datadisSupply.getValidDateFrom()) :
                                    null)
                            .build();
                    supply.setContract(contract);

                    updateSupplyRepository.update(supply);
                    LOGGER.info("Supply with code {} synchronized with datadis.", datadisSupply.getCups());
                } else {
                    LOGGER.warn("Datadis supply with CUPS {} not found as registered supply.", datadisSupply.getCups());
                }
            }
        }
    }
}
