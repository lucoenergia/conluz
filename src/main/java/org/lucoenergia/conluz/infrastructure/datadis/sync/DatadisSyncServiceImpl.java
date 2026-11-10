package org.lucoenergia.conluz.infrastructure.datadis.sync;

import org.apache.commons.lang3.StringUtils;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetConsumptionFromDatadisRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.persist.PersistDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.DatadisHourlyRecord;
import org.lucoenergia.conluz.domain.datadis.DatadisTokenRejectedException;
import org.lucoenergia.conluz.domain.datadis.get.GetDatadisConfigRepository;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncService;
import org.lucoenergia.conluz.domain.production.datadis.DatadisProduction;
import org.lucoenergia.conluz.domain.production.datadis.PersistDatadisProductionRepository;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.infrastructure.admin.supply.DatadisSupplyConfigurationException;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisAuthorizer;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisDisabledException;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisHourlyInstantResolver;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisHourlyResolution;
import org.lucoenergia.conluz.infrastructure.production.datadis.DatadisProductionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class DatadisSyncServiceImpl implements DatadisSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisSyncServiceImpl.class);

    private final GetConsumptionFromDatadisRepository getDatadisConsumptionRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final PersistDatadisConsumptionRepository persistDatadisConsumptionRepository;
    private final GetPlantRepository getPlantRepository;
    private final DatadisProductionMapper datadisProductionMapper;
    private final PersistDatadisProductionRepository persistDatadisProductionRepository;
    private final GetDatadisConfigRepository getDatadisConfigRepository;
    private final DatadisHourlyInstantResolver datadisHourlyInstantResolver;
    private final DatadisAuthorizer datadisAuthorizer;

    public DatadisSyncServiceImpl(GetConsumptionFromDatadisRepository getDatadisConsumptionRepository,
                                  GetSupplyRepository getSupplyRepository,
                                  PersistDatadisConsumptionRepository persistDatadisConsumptionRepository,
                                  GetPlantRepository getPlantRepository,
                                  DatadisProductionMapper datadisProductionMapper,
                                  PersistDatadisProductionRepository persistDatadisProductionRepository,
                                  GetDatadisConfigRepository getDatadisConfigRepository,
                                  DatadisHourlyInstantResolver datadisHourlyInstantResolver,
                                  DatadisAuthorizer datadisAuthorizer) {
        this.getDatadisConsumptionRepository = getDatadisConsumptionRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.persistDatadisConsumptionRepository = persistDatadisConsumptionRepository;
        this.getPlantRepository = getPlantRepository;
        this.datadisProductionMapper = datadisProductionMapper;
        this.persistDatadisProductionRepository = persistDatadisProductionRepository;
        this.getDatadisConfigRepository = getDatadisConfigRepository;
        this.datadisHourlyInstantResolver = datadisHourlyInstantResolver;
        this.datadisAuthorizer = datadisAuthorizer;
    }

    @Override
    public void synchronize(UUID communityId, LocalDate startDate, LocalDate endDate, String supplyCode) {
        DatadisConfig config = enabledConfig(communityId);

        if (supplyCode != null && !supplyCode.isBlank()) {
            synchronizeSupply(config, communityId, startDate, endDate, SupplyCode.of(supplyCode));
        } else {
            synchronizeCommunity(config, communityId, startDate, endDate);
        }
    }

    @Override
    public void synchronize(UUID communityId, LocalDate startDate, LocalDate endDate) {
        synchronizeCommunity(enabledConfig(communityId), communityId, startDate, endDate);
    }

    @Override
    public void synchronize(UUID communityId, LocalDate startDate, LocalDate endDate, SupplyCode supplyCode) {
        synchronizeSupply(enabledConfig(communityId), communityId, startDate, endDate, supplyCode);
    }

    private DatadisConfig enabledConfig(UUID communityId) {
        return getDatadisConfigRepository.findByCommunityId(communityId)
                .filter(config -> Boolean.TRUE.equals(config.getEnabled()))
                .orElseThrow(DatadisDisabledException::new);
    }

    private void synchronizeCommunity(DatadisConfig config, UUID communityId, LocalDate startDate, LocalDate endDate) {
        Set<String> plantCups = getPlantRepository.findSupplyCodesByCommunity(communityId);
        List<Supply> communitySupplies = getSupplyRepository.findAllByCommunityId(communityId);
        DatadisSession session = new DatadisSession(config);
        for (Supply supply : communitySupplies) {
            processSingleSupply(session, supply, startDate, endDate, plantCups);
        }
    }

    private void synchronizeSupply(DatadisConfig config, UUID communityId, LocalDate startDate, LocalDate endDate,
                                   SupplyCode supplyCode) {
        Supply supply = getSupplyRepository.findByCode(supplyCode)
                .orElseThrow(() -> new SupplyNotFoundException(supplyCode));
        if (supply.getCommunity() == null || !communityId.equals(supply.getCommunity().getId())) {
            throw new SupplyNotFoundException(supplyCode);
        }
        Set<String> plantCups = getPlantRepository.findSupplyCodesByCommunity(communityId);
        processSingleSupply(new DatadisSession(config), supply, startDate, endDate, plantCups);
    }

    private void processSingleSupply(DatadisSession session, Supply supply, LocalDate startDate, LocalDate endDate,
                                     Set<String> plantCups) {
        // Both are filled by the Datadis supplies sync: until then, the supply is skipped once per run, not refused
        // once per month
        if (supply.getDistributor() == null || StringUtils.isBlank(supply.getDistributor().getCode())
                || supply.getDistributor().getPointType() == null) {
            LOGGER.warn("Skipping Datadis consumption sync for supply {} in community {}: missing distributor code or "
                            + "point type", supply.getId(),
                    supply.getCommunity() != null ? supply.getCommunity().getId() : null);
            return;
        }

        LOGGER.info("Processing supply with ID: {}", supply.getId());

        boolean isPlantSupply = plantCups.contains(supply.getCode());

        LocalDate validDateFrom = startDate;

        while (validDateFrom.isBefore(endDate) || validDateFrom.isEqual(endDate)) {

            Month month = validDateFrom.getMonth();
            int year = validDateFrom.getYear();

            LOGGER.info("Processing month: {}/{}", month.getValue(), year);

            try {
                List<DatadisConsumption> consumptions = fetch(session, supply, month, year);

                if (!consumptions.isEmpty()) {
                    DatadisHourlyResolution resolution = datadisHourlyInstantResolver.resolve(consumptions);
                    warnAboutMismatchedDays(supply, resolution);
                    List<DatadisHourlyRecord<DatadisConsumption>> hourlyRecords = resolution.records();
                    if (isPlantSupply) {
                        persistProductionAndZeroedConsumption(hourlyRecords);
                    } else {
                        persistDatadisConsumptionRepository.persistHourlyConsumptions(hourlyRecords);
                    }
                    LOGGER.info("Hourly consumptions persisted");
                } else {
                    LOGGER.warn("Hourly consumptions are empty");
                }
            } catch (DatadisSupplyConfigurationException | DatadisTokenRejectedException e) {
                LOGGER.error("Unable to retrieve hourly consumptions of supply with ID {} for month {}/{}. Error: {}", supply.getId(),
                        month.getValue(), year, e.getMessage());
            }

            validDateFrom = validDateFrom.plusMonths(1);
        }
    }

    /**
     * A token Datadis rejects is renewed once, and the request retried with the new one; a second rejection is
     * thrown, failing that month of that supply.
     */
    private List<DatadisConsumption> fetch(DatadisSession session, Supply supply, Month month, int year) {
        try {
            return getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(session.config(),
                    session.bearerToken(), supply, month, year);
        } catch (DatadisTokenRejectedException e) {
            LOGGER.info("Datadis rejected the token of community {}, logging in again", session.config().getCommunityId());
            return getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(session.config(), session.renew(),
                    supply, month, year);
        }
    }

    /**
     * The Datadis login of one call: made on the first request, so a call with nothing to request does not log in,
     * and reused by every request after it, across supplies and months.
     */
    private final class DatadisSession {

        private final DatadisConfig config;
        private String bearerToken;

        private DatadisSession(DatadisConfig config) {
            this.config = config;
        }

        private DatadisConfig config() {
            return config;
        }

        private String bearerToken() {
            if (bearerToken == null) {
                bearerToken = "Bearer " + datadisAuthorizer.getAuthToken(config);
            }
            return bearerToken;
        }

        /**
         * Logs in again, and keeps the new token for the rest of the call.
         */
        private String renew() {
            bearerToken = "Bearer " + datadisAuthorizer.getAuthToken(config);
            return bearerToken;
        }
    }

    /**
     * One line per day whose records could not be placed by position. It names the supply by its id, never by its
     * CUPS.
     */
    private void warnAboutMismatchedDays(Supply supply, DatadisHourlyResolution resolution) {
        for (DatadisHourlyResolution.MismatchedDay day : resolution.mismatchedDays()) {
            LOGGER.warn("Datadis returned {} hourly records for supply with ID {} on {}, {} expected: each record is "
                            + "stored one hour before its label",
                    day.foundRecords(), supply.getId(), day.date(), day.expectedHours());
        }
    }

    /**
     * For a plant supply the Datadis surplus is the energy the plant produced, so it is stored as
     * production. The consumption is then persisted with its surplus zeroed out (on copies, leaving
     * the original DTOs untouched) to avoid counting the same energy as both surplus and production.
     * Both are stored at the instants already resolved for the consumption records.
     */
    private void persistProductionAndZeroedConsumption(List<DatadisHourlyRecord<DatadisConsumption>> consumptions) {
        List<DatadisHourlyRecord<DatadisProduction>> productions = new ArrayList<>(consumptions.size());
        List<DatadisHourlyRecord<DatadisConsumption>> zeroedConsumptions = new ArrayList<>(consumptions.size());
        for (DatadisHourlyRecord<DatadisConsumption> consumption : consumptions) {
            productions.add(new DatadisHourlyRecord<>(consumption.instant(),
                    datadisProductionMapper.map(consumption.record())));
            zeroedConsumptions.add(new DatadisHourlyRecord<>(consumption.instant(),
                    copyWithZeroSurplus(consumption.record())));
        }
        persistDatadisProductionRepository.persistHourlyProductions(productions);
        persistDatadisConsumptionRepository.persistHourlyConsumptions(zeroedConsumptions);
    }

    private DatadisConsumption copyWithZeroSurplus(DatadisConsumption original) {
        DatadisConsumption copy = new DatadisConsumption();
        copy.setCups(original.getCups());
        copy.setDate(original.getDate());
        copy.setTime(original.getTime());
        copy.setConsumptionKWh(original.getConsumptionKWh());
        copy.setObtainMethod(original.getObtainMethod());
        copy.setSurplusEnergyKWh(0f);
        copy.setGenerationEnergyKWh(original.getGenerationEnergyKWh());
        copy.setSelfConsumptionEnergyKWh(original.getSelfConsumptionEnergyKWh());
        return copy;
    }

}
