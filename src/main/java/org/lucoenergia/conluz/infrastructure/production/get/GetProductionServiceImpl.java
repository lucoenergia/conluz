package org.lucoenergia.conluz.infrastructure.production.get;

import org.lucoenergia.conluz.domain.production.get.GetProductionService;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.production.InstantProduction;
import org.lucoenergia.conluz.domain.production.ProductionByTime;
import org.lucoenergia.conluz.domain.production.get.GetProductionRepository;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
@Service
public class GetProductionServiceImpl implements GetProductionService {

    private final GetProductionRepository getProductionRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final GetPlantRepository getPlantRepository;

    public GetProductionServiceImpl(GetProductionRepository getProductionRepository,
                                GetSupplyRepository getSupplyRepository,
                                GetPlantRepository getPlantRepository) {
        this.getProductionRepository = getProductionRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.getPlantRepository = getPlantRepository;
    }

    @Override
    public List<ProductionByTime> getHourlyProductionByRangeOfDatesAndSupply(OffsetDateTime startDate,
                                                                         OffsetDateTime endDate, SupplyId id) {
        Optional<Supply> supply = getSupplyRepository.findById(id);
        if (supply.isEmpty()) {
            throw new SupplyNotFoundException(id);
        }

        return getProductionRepository.getHourlyProductionByRangeOfDates(startDate,
                endDate, supply.get().getPartitionCoefficient(), resolveStationCodes(supply.get()));
    }

    @Override
    public List<ProductionByTime> getDailyProductionByRangeOfDatesAndSupply(OffsetDateTime startDate,
                                                                         OffsetDateTime endDate, SupplyId id) {
        Optional<Supply> supply = getSupplyRepository.findById(id);
        if (supply.isEmpty()) {
            throw new SupplyNotFoundException(id);
        }

        return getProductionRepository.getDailyProductionByRangeOfDates(startDate,
                endDate, supply.get().getPartitionCoefficient(), resolveStationCodes(supply.get()));
    }

    @Override
    public List<ProductionByTime> getMonthlyProductionByRangeOfDatesAndSupply(OffsetDateTime startDate,
                                                                        OffsetDateTime endDate, SupplyId id) {
        Optional<Supply> supply = getSupplyRepository.findById(id);
        if (supply.isEmpty()) {
            throw new SupplyNotFoundException(id);
        }

        return getProductionRepository.getMonthlyProductionByRangeOfDates(startDate,
                endDate, supply.get().getPartitionCoefficient(), resolveStationCodes(supply.get()));
    }

    // --- Community-scoped variants ---

    @Override
    public InstantProduction getInstantProductionByCommunity(UUID communityId) {
        return getProductionRepository.getInstantProduction(getPlantRepository.findPlantCodesByCommunity(communityId));
    }

    @Override
    public InstantProduction getInstantProductionByCommunityAndSupply(UUID communityId, SupplyId id) {
        Supply supply = requireSupplyInCommunity(id, communityId);
        InstantProduction total = getProductionRepository.getInstantProduction(
                getPlantRepository.findPlantCodesByCommunity(communityId));
        return new InstantProduction(total.getPower() * supply.getPartitionCoefficient());
    }

    @Override
    public List<ProductionByTime> getHourlyProductionByRangeOfDatesAndCommunity(OffsetDateTime startDate,
                                                                                OffsetDateTime endDate, UUID communityId) {
        return getProductionRepository.getHourlyProductionByRangeOfDates(startDate, endDate, 1f,
                getPlantRepository.findPlantCodesByCommunity(communityId));
    }

    @Override
    public List<ProductionByTime> getHourlyProductionByRangeOfDatesAndCommunityAndSupply(OffsetDateTime startDate,
                                                                                         OffsetDateTime endDate,
                                                                                         UUID communityId, SupplyId id) {
        Supply supply = requireSupplyInCommunity(id, communityId);
        return getProductionRepository.getHourlyProductionByRangeOfDates(startDate, endDate,
                supply.getPartitionCoefficient(), getPlantRepository.findPlantCodesByCommunity(communityId));
    }

    @Override
    public List<ProductionByTime> getDailyProductionByRangeOfDatesAndCommunity(OffsetDateTime startDate,
                                                                               OffsetDateTime endDate, UUID communityId) {
        return getProductionRepository.getDailyProductionByRangeOfDates(startDate, endDate, 1f,
                getPlantRepository.findPlantCodesByCommunity(communityId));
    }

    @Override
    public List<ProductionByTime> getDailyProductionByRangeOfDatesAndCommunityAndSupply(OffsetDateTime startDate,
                                                                                        OffsetDateTime endDate,
                                                                                        UUID communityId, SupplyId id) {
        Supply supply = requireSupplyInCommunity(id, communityId);
        return getProductionRepository.getDailyProductionByRangeOfDates(startDate, endDate,
                supply.getPartitionCoefficient(), getPlantRepository.findPlantCodesByCommunity(communityId));
    }

    @Override
    public List<ProductionByTime> getMonthlyProductionByRangeOfDatesAndCommunity(OffsetDateTime startDate,
                                                                                 OffsetDateTime endDate, UUID communityId) {
        return getProductionRepository.getMonthlyProductionByRangeOfDates(startDate, endDate, 1f,
                getPlantRepository.findPlantCodesByCommunity(communityId));
    }

    @Override
    public List<ProductionByTime> getMonthlyProductionByRangeOfDatesAndCommunityAndSupply(OffsetDateTime startDate,
                                                                                          OffsetDateTime endDate,
                                                                                          UUID communityId, SupplyId id) {
        Supply supply = requireSupplyInCommunity(id, communityId);
        return getProductionRepository.getMonthlyProductionByRangeOfDates(startDate, endDate,
                supply.getPartitionCoefficient(), getPlantRepository.findPlantCodesByCommunity(communityId));
    }

    @Override
    public List<ProductionByTime> getYearlyProductionByRangeOfDatesAndCommunity(OffsetDateTime startDate,
                                                                                OffsetDateTime endDate, UUID communityId) {
        return getProductionRepository.getYearlyProductionByRangeOfDates(startDate, endDate, 1f,
                getPlantRepository.findPlantCodesByCommunity(communityId));
    }

    @Override
    public List<ProductionByTime> getYearlyProductionByRangeOfDatesAndCommunityAndSupply(OffsetDateTime startDate,
                                                                                         OffsetDateTime endDate,
                                                                                         UUID communityId, SupplyId id) {
        Supply supply = requireSupplyInCommunity(id, communityId);
        return getProductionRepository.getYearlyProductionByRangeOfDates(startDate, endDate,
                supply.getPartitionCoefficient(), getPlantRepository.findPlantCodesByCommunity(communityId));
    }

    /**
     * Resolves the InfluxDB station codes contributing to a supply's shared production.
     * Approximation: production is shared across the whole community via partition coefficients,
     * and most supplies own no plant of their own, so all plant codes of the supply's community are
     * used. A supply with no community resolves to no station codes (empty result, never an
     * unrestricted query) — defensive only; supplies.community_id is NOT NULL in the schema.
     * Replace with an exact lookup once the (plant_id, supply_id) participation table exists
     * (phase 2d of the Sharing Agreements epic) and the resolver that consumes it lands (phase 4).
     */
    private List<String> resolveStationCodes(Supply supply) {
        if (supply.getCommunity() == null) {
            return List.of();
        }
        return getPlantRepository.findPlantCodesByCommunity(supply.getCommunity().getId());
    }

    /**
     * Loads the supply and verifies it belongs to the given community. A supply that does not exist
     * or belongs to another community yields a {@link SupplyNotFoundException} (404) so the caller
     * cannot probe supplies outside their community.
     */
    private Supply requireSupplyInCommunity(SupplyId id, UUID communityId) {
        Supply supply = getSupplyRepository.findById(id).orElseThrow(() -> new SupplyNotFoundException(id));
        if (supply.getCommunity() == null || !communityId.equals(supply.getCommunity().getId())) {
            throw new SupplyNotFoundException(id);
        }
        return supply;
    }
}