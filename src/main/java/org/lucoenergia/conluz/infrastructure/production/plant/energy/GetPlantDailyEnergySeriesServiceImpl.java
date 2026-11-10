package org.lucoenergia.conluz.infrastructure.production.plant.energy;

import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.production.datadis.get.DailyProductionAggregate;
import org.lucoenergia.conluz.domain.production.datadis.get.GetDatadisProductionAggregateRepository;
import org.lucoenergia.conluz.domain.production.datadis.get.ProductionAggregate;
import org.lucoenergia.conluz.domain.production.plant.energy.GetPlantDailyEnergySeriesService;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyScope;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyScopeResolver;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergySeriesBucket;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves the plant's backing supply and period through the same scope resolver as the plant energy
 * metrics, reads the supply's records totalled per local day, and emits one bucket per local day
 * overlapping the period. A day the period starts or ends inside counts only its hours inside the
 * period.
 *
 * <h2>Query cost</h2>
 *
 * <p>One relational query (the plant), then a single InfluxDB query, grouped by day inside the store.
 */
@Service
@Transactional(readOnly = true)
public class GetPlantDailyEnergySeriesServiceImpl implements GetPlantDailyEnergySeriesService {

    private final PlantEnergyScopeResolver plantEnergyScopeResolver;
    private final GetDatadisProductionAggregateRepository getDatadisProductionAggregateRepository;

    public GetPlantDailyEnergySeriesServiceImpl(
            PlantEnergyScopeResolver plantEnergyScopeResolver,
            GetDatadisProductionAggregateRepository getDatadisProductionAggregateRepository) {
        this.plantEnergyScopeResolver = plantEnergyScopeResolver;
        this.getDatadisProductionAggregateRepository = getDatadisProductionAggregateRepository;
    }

    @Override
    public List<PlantEnergySeriesBucket> getDailySeries(UUID plantId, OffsetDateTime startDate,
                                                        OffsetDateTime endDate) {

        PlantEnergyScope scope = plantEnergyScopeResolver.resolve(plantId, startDate, endDate, null,
                EnergyMetricsPeriodValidator.MAX_LENGTH);

        // Explicit dates always resolve a period, its end clipped to the current hour. A scope without
        // one is a broken invariant, not an empty series.
        return scope.getPeriod()
                .map(period -> getDailySeries(scope.getSupplyCode(), scope.getZone(), period))
                .orElseThrow(() -> new IllegalStateException(
                        "The plant daily energy series resolved a scope from explicit bounds without a "
                        + "period; explicit bounds always resolve one"));
    }

    private List<PlantEnergySeriesBucket> getDailySeries(String supplyCode, ZoneId zone,
                                                         EnergyMetricsPeriod period) {
        Map<LocalDate, DailyProductionAggregate> days = getDatadisProductionAggregateRepository
                .aggregateByLocalDay(supplyCode, period.getStartDate().toInstant(), period.getEndDate().toInstant(),
                        zone)
                .stream()
                .collect(Collectors.toMap(DailyProductionAggregate::day, Function.identity()));

        return period.daysOverlapping(zone).stream()
                .map(day -> PlantEnergySeriesBucket.of(day.atStartOfDay(zone).toOffsetDateTime(),
                        period.hoursOverlapping(EnergyMetricsPeriod.ofDay(day, zone)),
                        Optional.ofNullable(days.get(day))
                                .map(total -> new ProductionAggregate(total.productionKWh(), total.hoursWithData()))))
                .toList();
    }
}
