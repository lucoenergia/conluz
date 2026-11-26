package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.PlantReference;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyReference;
import org.lucoenergia.conluz.domain.consumption.ReferenceMonthResolver;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DailyPublishedAggregate;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.price.get.GetPriceRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.GetPlantSurplusClaimService;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.PlantSurplusClaim;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.PlantSurplusClaimCalculator;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimAvailableMonths;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimInput;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimMonthNotClosedException;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reads what a plant's surplus claim needs and hands it to {@link PlantSurplusClaimCalculator}.
 *
 * <p>The supplies in scope hold an applied coefficient of the plant overlapping the month, the plant's own supply
 * excluded: the sync stores its surplus as 0 and its production apart. The month is read in the zone of the plant's
 * community.
 *
 * <h2>Query cost</h2>
 *
 * <p>Three relational queries (the plant, its coefficients, the supplies that ever held an applied one), whatever the
 * number of supplies. The latest published month costs what {@link ReferenceMonthResolver} documents: two InfluxDB
 * queries plus one per month it visits. The month itself costs two InfluxDB queries (the hourly surplus and the daily
 * record counts of every supply in scope) and, when any surplus was recorded, a third for the prices.
 */
@Service
@Transactional(readOnly = true)
public class GetPlantSurplusClaimServiceImpl implements GetPlantSurplusClaimService {

    private final GetPlantRepository getPlantRepository;
    private final GetSupplyPartitionCoefficientRepository getSupplyPartitionCoefficientRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final ReferenceMonthResolver referenceMonthResolver;
    private final GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository;
    private final GetPriceRepository getPriceRepository;
    private final ZoneResolver zoneResolver;
    private final Clock clock;

    public GetPlantSurplusClaimServiceImpl(GetPlantRepository getPlantRepository,
                                           GetSupplyPartitionCoefficientRepository getSupplyPartitionCoefficientRepository,
                                           GetSupplyRepository getSupplyRepository,
                                           ReferenceMonthResolver referenceMonthResolver,
                                           GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository,
                                           GetPriceRepository getPriceRepository,
                                           ZoneResolver zoneResolver,
                                           Clock clock) {
        this.getPlantRepository = getPlantRepository;
        this.getSupplyPartitionCoefficientRepository = getSupplyPartitionCoefficientRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.referenceMonthResolver = referenceMonthResolver;
        this.getDatadisConsumptionAggregateRepository = getDatadisConsumptionAggregateRepository;
        this.getPriceRepository = getPriceRepository;
        this.zoneResolver = zoneResolver;
        this.clock = clock;
    }

    @Override
    public PlantSurplusClaim getSurplusClaim(UUID plantId, YearMonth requestedMonth) {
        PlantId id = PlantId.of(plantId);
        Plant plant = getPlantRepository.findById(id).orElseThrow(() -> new PlantNotFoundException(id));
        ZoneId zone = zoneResolver.resolveZoneIdForCommunity(plant.getCommunity().getId());
        YearMonth currentMonth = YearMonth.now(clock.withZone(zone));
        if (requestedMonth != null && !requestedMonth.isBefore(currentMonth)) {
            throw new SurplusClaimMonthNotClosedException(requestedMonth, currentMonth);
        }

        UUID plantSupplyId = plant.getSupply().getId();
        List<SupplyPartitionCoefficient> applied = getSupplyPartitionCoefficientRepository.findAllByPlantId(plantId)
                .stream()
                .filter(coefficient -> coefficient.getValidFrom() != null)
                .toList();
        Set<UUID> everHeldIds = applied.stream()
                .map(SupplyPartitionCoefficient::getSupplyId)
                .filter(supplyId -> !supplyId.equals(plantSupplyId))
                .collect(Collectors.toSet());
        List<Supply> everHeld = getSupplyRepository.findAllByIds(everHeldIds).stream()
                .sorted(Comparator.comparing(Supply::getCode))
                .toList();

        YearMonth firstMonth = applied.stream()
                .map(SupplyPartitionCoefficient::getValidFrom)
                .min(Comparator.naturalOrder())
                .map(validFrom -> YearMonth.from(validFrom.atZone(zone)))
                .orElse(null);
        YearMonth latestPublishedMonth = referenceMonthResolver.resolveLatestPublishedMonth(everHeld, zone)
                .orElse(null);
        YearMonth month = requestedMonth != null
                ? requestedMonth
                : latestPublishedMonth != null ? latestPublishedMonth : currentMonth.minusMonths(1);

        Instant monthStart = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant nextMonthStart = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        Set<UUID> inScopeIds = applied.stream()
                .filter(coefficient -> PlantSurplusClaimCalculator.overlaps(coefficient, monthStart, nextMonthStart))
                .map(SupplyPartitionCoefficient::getSupplyId)
                .filter(supplyId -> !supplyId.equals(plantSupplyId))
                .collect(Collectors.toSet());
        List<Supply> inScope = everHeld.stream()
                .filter(supply -> inScopeIds.contains(supply.getId()))
                .toList();

        Map<Instant, Double> surplusByHour = getDatadisConsumptionAggregateRepository.sumRecordedSurplusByHour(inScope,
                monthStart, nextMonthStart, zone);
        Map<String, List<DailyPublishedAggregate>> daysBySupplyCode =
                getDatadisConsumptionAggregateRepository.aggregateByLocalDay(inScope, monthStart, nextMonthStart, zone);
        // Without any surplus there is no income to price.
        Map<Instant, Double> meanPriceByHour = surplusByHour.isEmpty()
                ? Map.of()
                : getPriceRepository.meanPriceByHour(monthStart, nextMonthStart, zone);

        return PlantSurplusClaimCalculator.calculate(new SurplusClaimInput(
                month,
                zone,
                new PlantReference(plant.getId(), plant.getName()),
                plant.getCil(),
                plant.getRegulatoryCode(),
                plantSupplyId,
                plant.getNominalPowerKW(),
                new SurplusClaimAvailableMonths(firstMonth, latestPublishedMonth),
                applied,
                inScope.stream()
                        .map(supply -> new SupplyReference(supply.getId(), supply.getCode(), supply.getName()))
                        .toList(),
                surplusByHour,
                daysBySupplyCode,
                meanPriceByHour));
    }
}
