package org.lucoenergia.conluz.infrastructure.production.get;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientResolver;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientSegment;
import org.lucoenergia.conluz.domain.production.InstantProduction;
import org.lucoenergia.conluz.domain.production.ProductionByTime;
import org.lucoenergia.conluz.domain.production.get.GetProductionRepository;
import org.lucoenergia.conluz.domain.production.get.GetProductionService;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiConsumer;

@Transactional(readOnly = true)
@Service
public class GetProductionServiceImpl implements GetProductionService {

    private final GetProductionRepository getProductionRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final GetPlantRepository getPlantRepository;
    private final CoefficientResolver coefficientResolver;
    private final TimeConfiguration timeConfiguration;

    public GetProductionServiceImpl(GetProductionRepository getProductionRepository,
                                GetSupplyRepository getSupplyRepository,
                                GetPlantRepository getPlantRepository,
                                CoefficientResolver coefficientResolver,
                                TimeConfiguration timeConfiguration) {
        this.getProductionRepository = getProductionRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.getPlantRepository = getPlantRepository;
        this.coefficientResolver = coefficientResolver;
        this.timeConfiguration = timeConfiguration;
    }

    // --- Supply-scoped variants ---

    @Override
    public List<ProductionByTime> getHourlyProductionByRangeOfDatesAndSupply(OffsetDateTime startDate,
                                                                         OffsetDateTime endDate, SupplyId id) {
        requireSupplyExists(id);
        return hourlyProductionForSupply(startDate, endDate, id.getId());
    }

    @Override
    public List<ProductionByTime> getDailyProductionByRangeOfDatesAndSupply(OffsetDateTime startDate,
                                                                         OffsetDateTime endDate, SupplyId id) {
        requireSupplyExists(id);
        return dailyProductionForSupply(startDate, endDate, id.getId());
    }

    @Override
    public List<ProductionByTime> getMonthlyProductionByRangeOfDatesAndSupply(OffsetDateTime startDate,
                                                                        OffsetDateTime endDate, SupplyId id) {
        requireSupplyExists(id);
        return monthlyProductionForSupply(startDate, endDate, id.getId());
    }

    // --- Community-scoped variants ---

    @Override
    public InstantProduction getInstantProductionByCommunity(UUID communityId) {
        return getProductionRepository.getInstantProduction(getPlantRepository.findPlantProviderCodesByCommunity(communityId));
    }

    @Override
    public InstantProduction getInstantProductionByCommunityAndSupply(UUID communityId, SupplyId id) {
        Supply supply = requireSupplyInCommunity(id, communityId);
        return instantProductionForSupply(supply.getId());
    }

    @Override
    public List<ProductionByTime> getHourlyProductionByRangeOfDatesAndCommunity(OffsetDateTime startDate,
                                                                                OffsetDateTime endDate, UUID communityId) {
        return getProductionRepository.getHourlyProductionByRangeOfDates(startDate, endDate,
                getPlantRepository.findPlantProviderCodesByCommunity(communityId));
    }

    @Override
    public List<ProductionByTime> getHourlyProductionByRangeOfDatesAndCommunityAndSupply(OffsetDateTime startDate,
                                                                                         OffsetDateTime endDate,
                                                                                         UUID communityId, SupplyId id) {
        Supply supply = requireSupplyInCommunity(id, communityId);
        return hourlyProductionForSupply(startDate, endDate, supply.getId());
    }

    @Override
    public List<ProductionByTime> getDailyProductionByRangeOfDatesAndCommunity(OffsetDateTime startDate,
                                                                               OffsetDateTime endDate, UUID communityId) {
        return getProductionRepository.getDailyProductionByRangeOfDates(startDate, endDate,
                getPlantRepository.findPlantProviderCodesByCommunity(communityId));
    }

    @Override
    public List<ProductionByTime> getDailyProductionByRangeOfDatesAndCommunityAndSupply(OffsetDateTime startDate,
                                                                                        OffsetDateTime endDate,
                                                                                        UUID communityId, SupplyId id) {
        Supply supply = requireSupplyInCommunity(id, communityId);
        return dailyProductionForSupply(startDate, endDate, supply.getId());
    }

    @Override
    public List<ProductionByTime> getMonthlyProductionByRangeOfDatesAndCommunity(OffsetDateTime startDate,
                                                                                 OffsetDateTime endDate, UUID communityId) {
        return getProductionRepository.getMonthlyProductionByRangeOfDates(startDate, endDate,
                getPlantRepository.findPlantProviderCodesByCommunity(communityId));
    }

    @Override
    public List<ProductionByTime> getMonthlyProductionByRangeOfDatesAndCommunityAndSupply(OffsetDateTime startDate,
                                                                                          OffsetDateTime endDate,
                                                                                          UUID communityId, SupplyId id) {
        Supply supply = requireSupplyInCommunity(id, communityId);
        return monthlyProductionForSupply(startDate, endDate, supply.getId());
    }

    @Override
    public List<ProductionByTime> getYearlyProductionByRangeOfDatesAndCommunity(OffsetDateTime startDate,
                                                                                OffsetDateTime endDate, UUID communityId) {
        return getProductionRepository.getYearlyProductionByRangeOfDates(startDate, endDate,
                getPlantRepository.findPlantProviderCodesByCommunity(communityId));
    }

    @Override
    public List<ProductionByTime> getYearlyProductionByRangeOfDatesAndCommunityAndSupply(OffsetDateTime startDate,
                                                                                         OffsetDateTime endDate,
                                                                                         UUID communityId, SupplyId id) {
        Supply supply = requireSupplyInCommunity(id, communityId);
        return yearlyProductionForSupply(startDate, endDate, supply.getId());
    }

    // --- Per-supply production, resolver-driven ---
    //
    // AUTHORIZATION-SURFACE NOTE: the plant set for a supply's production is derived entirely from
    // supply_partition_coefficient (via CoefficientResolver), not from the supply's community. This
    // intentionally moves the ownership boundary: community membership used to be a second line of
    // defence (production was always scoped to "plants in the supply's community"); now, a wrong row
    // in supply_partition_coefficient could in principle let one community's production reach another
    // community's supply, with no second check. This is intentional -- the epic's model states
    // (plant_id, supply_id) IS the participation relation, so the coefficient table is the sole
    // authority for per-supply production -- but it is a deliberate asymmetry with the CONSUMPTION
    // path (Datadis-based), which remains community-scoped. Do not "fix" this by reintroducing a
    // community filter here.
    //
    // GRANULARITY BOUNDARY NOTE: daily stays UTC-day-aligned (matching today's behaviour) while
    // monthly/yearly use Europe/Madrid calendar boundaries (see monthlyProductionForSupply /
    // yearlyProductionForSupply). A month's 31 daily (UTC) totals will therefore NOT sum to that
    // month's (Madrid) total -- tracked as a follow-up (reconcile daily to Madrid-day alignment); do
    // not "fix" this as a drive-by change here.

    private InstantProduction instantProductionForSupply(UUID supplyId) {
        Instant now = timeConfiguration.now().toInstant();
        Map<UUID, BigDecimal> coefficientsByPlant = coefficientResolver.resolveCoefficientsBySupplyAtInstant(supplyId, now);

        double power = 0d;
        for (Map.Entry<UUID, BigDecimal> entry : coefficientsByPlant.entrySet()) {
            Optional<String> stationCode = providerCodeOf(entry.getKey());
            if (stationCode.isEmpty()) {
                continue;
            }
            InstantProduction plantProduction = getProductionRepository.getInstantProduction(List.of(stationCode.get()));
            power += plantProduction.getPower() * entry.getValue().doubleValue();
        }
        return new InstantProduction(power);
    }

    private List<ProductionByTime> hourlyProductionForSupply(OffsetDateTime startDate, OffsetDateTime endDate, UUID supplyId) {
        Instant exclusiveTo = exclusiveTo(endDate);
        Map<UUID, List<CoefficientSegment>> segmentsByPlant =
                coefficientResolver.resolveSegmentsBySupply(supplyId, startDate.toInstant(), exclusiveTo);

        TreeMap<OffsetDateTime, Double> merged = new TreeMap<>();
        for (Map.Entry<UUID, List<CoefficientSegment>> entry : segmentsByPlant.entrySet()) {
            Optional<String> stationCode = providerCodeOf(entry.getKey());
            if (stationCode.isEmpty()) {
                continue;
            }
            List<CoefficientSegment> segments = entry.getValue(); // ordered by from
            List<ProductionByTime> rawPoints = getProductionRepository.getHourlyProductionHalfOpen(
                    startDate, toOffsetDateTime(exclusiveTo), List.of(stationCode.get()));

            int segmentIndex = 0;
            for (ProductionByTime point : rawPoints) {
                Instant pointInstant = point.getTime().toInstant();
                while (segmentIndex < segments.size() && !pointInstant.isBefore(segments.get(segmentIndex).to())) {
                    segmentIndex++;
                }
                if (segmentIndex >= segments.size()) {
                    break; // no later segment can cover this or any subsequent point
                }
                if (pointInstant.isBefore(segments.get(segmentIndex).from())) {
                    continue; // uncovered instant, contributes 0
                }
                double contribution = point.getPower() * segments.get(segmentIndex).coefficient().doubleValue();
                merged.merge(point.getTime(), contribution, Double::sum);
            }
        }
        return toProductionByTimeList(merged);
    }

    private List<ProductionByTime> dailyProductionForSupply(OffsetDateTime startDate, OffsetDateTime endDate, UUID supplyId) {
        Instant exclusiveTo = exclusiveTo(endDate);
        Map<UUID, List<CoefficientSegment>> segmentsByPlant =
                coefficientResolver.resolveSegmentsBySupply(supplyId, startDate.toInstant(), exclusiveTo);

        TreeMap<OffsetDateTime, Double> merged = new TreeMap<>();
        for (Map.Entry<UUID, List<CoefficientSegment>> entry : segmentsByPlant.entrySet()) {
            Optional<String> stationCode = providerCodeOf(entry.getKey());
            if (stationCode.isEmpty()) {
                continue;
            }
            for (CoefficientSegment segment : entry.getValue()) {
                List<ProductionByTime> segmentDaily = getProductionRepository.getDailyProductionHalfOpen(
                        toOffsetDateTime(segment.from()), toOffsetDateTime(segment.to()), List.of(stationCode.get()));
                double coefficient = segment.coefficient().doubleValue();
                for (ProductionByTime bucket : segmentDaily) {
                    merged.merge(bucket.getTime(), bucket.getPower() * coefficient, Double::sum);
                }
            }
        }
        return toProductionByTimeList(merged);
    }

    private List<ProductionByTime> monthlyProductionForSupply(OffsetDateTime startDate, OffsetDateTime endDate, UUID supplyId) {
        ZoneId zone = timeConfiguration.getZoneId();
        TreeMap<YearMonth, Double> merged = new TreeMap<>();
        forEachMadridAlignedSegmentDay(startDate, endDate, supplyId, (dayBucket, contribution) ->
                merged.merge(YearMonth.from(dayBucket.getTime()), contribution, Double::sum));

        List<ProductionByTime> result = new ArrayList<>();
        for (Map.Entry<YearMonth, Double> entry : merged.entrySet()) {
            OffsetDateTime bucketStart = entry.getKey().atDay(1).atStartOfDay(zone).toOffsetDateTime();
            result.add(new ProductionByTime(bucketStart, entry.getValue()));
        }
        return result;
    }

    private List<ProductionByTime> yearlyProductionForSupply(OffsetDateTime startDate, OffsetDateTime endDate, UUID supplyId) {
        ZoneId zone = timeConfiguration.getZoneId();
        TreeMap<Year, Double> merged = new TreeMap<>();
        forEachMadridAlignedSegmentDay(startDate, endDate, supplyId, (dayBucket, contribution) ->
                merged.merge(Year.from(dayBucket.getTime()), contribution, Double::sum));

        List<ProductionByTime> result = new ArrayList<>();
        for (Map.Entry<Year, Double> entry : merged.entrySet()) {
            OffsetDateTime bucketStart = entry.getKey().atDay(1).atStartOfDay(zone).toOffsetDateTime();
            result.add(new ProductionByTime(bucketStart, entry.getValue()));
        }
        return result;
    }

    /**
     * Shared plant/segment iteration for monthly and yearly: resolves segments once, fetches one
     * Madrid-calendar-day-aligned, half-open query per segment (one query per segment, not per output
     * bucket), and hands each returned day -- already an exact Madrid calendar day -- to
     * {@code accumulator} with its β-scaled contribution, for the caller to fold into its own
     * YearMonth/Year bucket map.
     */
    private void forEachMadridAlignedSegmentDay(OffsetDateTime startDate, OffsetDateTime endDate, UUID supplyId,
                                                 BiConsumer<ProductionByTime, Double> accumulator) {
        Instant exclusiveTo = exclusiveTo(endDate);
        Map<UUID, List<CoefficientSegment>> segmentsByPlant =
                coefficientResolver.resolveSegmentsBySupply(supplyId, startDate.toInstant(), exclusiveTo);

        for (Map.Entry<UUID, List<CoefficientSegment>> entry : segmentsByPlant.entrySet()) {
            Optional<String> stationCode = providerCodeOf(entry.getKey());
            if (stationCode.isEmpty()) {
                continue;
            }
            for (CoefficientSegment segment : entry.getValue()) {
                List<ProductionByTime> segmentDaily = getProductionRepository.getMadridAlignedDailyProductionHalfOpen(
                        toOffsetDateTime(segment.from()), toOffsetDateTime(segment.to()), List.of(stationCode.get()));
                double coefficient = segment.coefficient().doubleValue();
                for (ProductionByTime dayBucket : segmentDaily) {
                    accumulator.accept(dayBucket, dayBucket.getPower() * coefficient);
                }
            }
        }
    }

    private List<ProductionByTime> toProductionByTimeList(TreeMap<OffsetDateTime, Double> merged) {
        List<ProductionByTime> result = new ArrayList<>();
        for (Map.Entry<OffsetDateTime, Double> entry : merged.entrySet()) {
            result.add(new ProductionByTime(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private Optional<String> providerCodeOf(UUID plantId) {
        return getPlantRepository.findById(PlantId.of(plantId)).map(Plant::getProviderCode);
    }

    /**
     * Converts the API's inclusive {@code endDate} into the exclusive upper bound the segment/query
     * machinery uses uniformly. Production data in this system is never sub-second resolution, so
     * nudging by one nanosecond is a lossless conversion, not an approximation. This is the single
     * place this conversion happens; every downstream call (segment resolution, half-open fetches,
     * per-segment clamped ranges) uses the resulting instant, so the fetched point set and the
     * segment-covered instant set are the same set of instants by construction.
     */
    private static Instant exclusiveTo(OffsetDateTime endDate) {
        return endDate.toInstant().plusNanos(1);
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    /**
     * Loads the supply and verifies it exists. A missing supply yields a {@link SupplyNotFoundException}
     * (404).
     */
    private void requireSupplyExists(SupplyId id) {
        if (getSupplyRepository.findById(id).isEmpty()) {
            throw new SupplyNotFoundException(id);
        }
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
