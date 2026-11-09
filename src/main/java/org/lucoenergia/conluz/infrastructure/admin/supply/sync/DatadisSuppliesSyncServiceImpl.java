package org.lucoenergia.conluz.infrastructure.admin.supply.sync;

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
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisAuthorizer;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisDisabledException;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    @Override
    public void synchronizeSupplies(UUID communityId) {
        DatadisConfig config = getDatadisConfigRepository.findByCommunityId(communityId)
                .filter(found -> Boolean.TRUE.equals(found.getEnabled()))
                .orElseThrow(DatadisDisabledException::new);

        // A disabled supply is not synced: it is neither matched, nor updated, nor reported as missing
        List<Supply> registered = getSupplyRepository.findAllByCommunityId(communityId).stream()
                .filter(supply -> Boolean.TRUE.equals(supply.getEnabled()))
                .toList();
        Map<String, List<Supply>> registeredByCups = registered.stream()
                .collect(Collectors.groupingBy(supply -> cupsKey(supply.getCode())));
        // Only the owners of this community's supplies are processed, and only this community's
        // supplies can be updated, so the job never writes another community's supplies.
        List<User> owners = registered.stream()
                .map(Supply::getUser)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a))
                .values().stream()
                .toList();

        // One login per community and run: every owner is requested with the same token
        String bearerToken = "Bearer " + datadisAuthorizer.getAuthToken(config);

        List<DatadisSupply> entries = new ArrayList<>();
        for (User owner : owners) {
            LOGGER.info("Processing users with ID {}", owner.getId());
            entries.addAll(getSupplyRepositoryDatadis.getSuppliesByUser(config, bearerToken, owner));
        }

        Set<String> returned = new HashSet<>();
        int notRegistered = 0;
        for (DatadisSupply entry : entries) {
            String key = cupsKey(entry.getCups());
            returned.add(key);
            List<Supply> matches = registeredByCups.getOrDefault(key, List.of());
            if (matches.isEmpty()) {
                // Datadis returns every supply authorised for an owner, also those of other communities
                notRegistered++;
            } else if (matches.size() > 1) {
                LOGGER.error("Datadis supply matches several registered supplies in community {}: {}", communityId,
                        matches.stream().map(Supply::getId).toList());
            } else {
                update(matches.get(0), entry);
            }
        }
        LOGGER.debug("{} Datadis supplies not registered in community {}", notRegistered, communityId);
        registeredByCups.forEach((key, supplies) -> {
            if (!returned.contains(key)) {
                supplies.forEach(supply -> LOGGER.warn("Registered supply {} not returned by Datadis in community {}",
                        supply.getId(), communityId));
            }
        });
    }

    /**
     * The key under which a code and a Datadis CUPS are the same supply: upper-cased, and in its 22-character form when
     * it has one, so that the two forms of a CUPS meet (SUP-007). A code that is not a CUPS, stored before #532, is
     * only upper-cased; nothing here throws.
     */
    private static String cupsKey(String code) {
        if (code == null) {
            return "";
        }
        String upperCased = code.toUpperCase(Locale.ROOT);
        return SupplyCode.toFullLength(upperCased).orElse(upperCased);
    }

    private void update(Supply supply, DatadisSupply datadisSupply) {
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
        LOGGER.info("Supply with ID {} synchronized with datadis.", supply.getId());
    }
}
