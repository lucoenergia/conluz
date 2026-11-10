package org.lucoenergia.conluz.infrastructure.admin.supply.sync;

import org.lucoenergia.conluz.domain.admin.supply.DatadisSupply;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepositoryDatadis;
import org.lucoenergia.conluz.domain.admin.supply.sync.DatadisSuppliesSyncService;
import org.lucoenergia.conluz.domain.admin.supply.sync.DatadisSupplySelection;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.get.GetDatadisConfigRepository;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisAuthorizer;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisDisabledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Not transactional: Datadis is called with no transaction open, and each supply is written in a transaction of its
 * own ({@link DatadisSupplyUpdateStep}).
 */
@Service
public class DatadisSuppliesSyncServiceImpl implements DatadisSuppliesSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisSuppliesSyncServiceImpl.class);

    private final GetSupplyRepository getSupplyRepository;
    private final DatadisSupplyUpdateStep datadisSupplyUpdateStep;
    private final GetSupplyRepositoryDatadis getSupplyRepositoryDatadis;
    private final GetDatadisConfigRepository getDatadisConfigRepository;
    private final DatadisAuthorizer datadisAuthorizer;

    public DatadisSuppliesSyncServiceImpl(GetSupplyRepository getSupplyRepository,
                                          DatadisSupplyUpdateStep datadisSupplyUpdateStep,
                                          GetSupplyRepositoryDatadis getSupplyRepositoryDatadis,
                                          GetDatadisConfigRepository getDatadisConfigRepository,
                                          DatadisAuthorizer datadisAuthorizer) {
        this.getSupplyRepository = getSupplyRepository;
        this.datadisSupplyUpdateStep = datadisSupplyUpdateStep;
        this.getSupplyRepositoryDatadis = getSupplyRepositoryDatadis;
        this.getDatadisConfigRepository = getDatadisConfigRepository;
        this.datadisAuthorizer = datadisAuthorizer;
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

        // Datadis returns one entry per contract of a CUPS: the supply is synced from the latest one
        Map<String, List<DatadisSupply>> entriesByCups = entries.stream()
                .collect(Collectors.groupingBy(entry -> cupsKey(entry.getCups())));
        int notRegistered = 0;
        for (Map.Entry<String, List<DatadisSupply>> cups : entriesByCups.entrySet()) {
            DatadisSupply entry = DatadisSupplySelection.latest(cups.getValue());
            List<Supply> matches = registeredByCups.getOrDefault(cups.getKey(), List.of());
            if (matches.isEmpty()) {
                // Datadis returns every supply authorised for an owner, also those of other communities
                notRegistered++;
            } else if (matches.size() > 1) {
                LOGGER.error("Datadis supply matches several registered supplies in community {}: {}", communityId,
                        matches.stream().map(Supply::getId).toList());
            } else {
                update(communityId, matches.get(0), entry);
            }
        }
        LOGGER.debug("{} Datadis supplies not registered in community {}", notRegistered, communityId);
        registeredByCups.forEach((key, supplies) -> {
            if (!entriesByCups.containsKey(key)) {
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

    /**
     * A supply that cannot be written is logged, and does not stop the others.
     */
    private void update(UUID communityId, Supply supply, DatadisSupply datadisSupply) {
        try {
            datadisSupplyUpdateStep.update(SupplyId.of(supply.getId()), datadisSupply);
            LOGGER.info("Supply with ID {} synchronized with datadis.", supply.getId());
        } catch (Exception e) {
            LOGGER.error("Failed to update supply from Datadis in community {}: {}", communityId, supply.getId(), e);
        }
    }
}
