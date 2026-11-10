package org.lucoenergia.conluz.infrastructure.production.plant.energy;

import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.production.datadis.get.DailyProductionAggregate;
import org.lucoenergia.conluz.domain.production.datadis.get.GetDatadisProductionAggregateRepository;
import org.lucoenergia.conluz.domain.production.datadis.get.ProductionAggregate;
import org.lucoenergia.conluz.domain.production.plant.energy.GetPlantMonthlyEnergySeriesService;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyScope;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyScopeResolver;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergySeriesBucket;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the plant's backing supply and period through the same scope resolver as the plant energy
 * metrics, reads the supply's records totalled per local day in a single read, folds the days into
 * local calendar months, and emits one bucket per month whose local midnight on day 1 falls inside the
 * period. The monthly pre-aggregates are not read, since they cannot tell an hour nothing was stored
 * for from a stored zero.
 *
 * <h2>Query cost</h2>
 *
 * <p>One relational query (the plant), then a single InfluxDB query, grouped by day inside the store:
 * at most 731 rows for the longest period.
 */
@Service
@Transactional(readOnly = true)
public class GetPlantMonthlyEnergySeriesServiceImpl implements GetPlantMonthlyEnergySeriesService {

    private final PlantEnergyScopeResolver plantEnergyScopeResolver;
    private final GetDatadisProductionAggregateRepository getDatadisProductionAggregateRepository;

    public GetPlantMonthlyEnergySeriesServiceImpl(
            PlantEnergyScopeResolver plantEnergyScopeResolver,
            GetDatadisProductionAggregateRepository getDatadisProductionAggregateRepository) {
        this.plantEnergyScopeResolver = plantEnergyScopeResolver;
        this.getDatadisProductionAggregateRepository = getDatadisProductionAggregateRepository;
    }

    @Override
    public List<PlantEnergySeriesBucket> getMonthlySeries(UUID plantId, OffsetDateTime startDate,
                                                          OffsetDateTime endDate) {

        PlantEnergyScope scope = plantEnergyScopeResolver.resolve(plantId, startDate, endDate, null,
                EnergyMetricsPeriodValidator.MAX_LENGTH);

        // Explicit dates always resolve a period, its end clipped to the current hour. A scope without
        // one is a broken invariant, not an empty series.
        return scope.getPeriod()
                .map(period -> getMonthlySeries(scope.getSupplyCode(), scope.getZone(), period))
                .orElseThrow(() -> new IllegalStateException(
                        "The plant monthly energy series resolved a scope from explicit bounds without a "
                        + "period; explicit bounds always resolve one"));
    }

    private List<PlantEnergySeriesBucket> getMonthlySeries(String supplyCode, ZoneId zone,
                                                           EnergyMetricsPeriod period) {
        Map<YearMonth, ProductionAggregate> months = foldIntoMonths(getDatadisProductionAggregateRepository
                .aggregateByLocalDay(supplyCode, period.getStartDate().toInstant(), period.getEndDate().toInstant(),
                        zone));

        // A day before the first selected month belongs to no bucket.
        return period.monthsStartingIn(zone).stream()
                .map(month -> PlantEnergySeriesBucket.of(month.atDay(1).atStartOfDay(zone).toOffsetDateTime(),
                        period.hoursOverlapping(EnergyMetricsPeriod.ofMonth(month, zone)),
                        Optional.ofNullable(months.get(month))))
                .toList();
    }

    /**
     * The days added up per local calendar month, unrounded.
     */
    private static Map<YearMonth, ProductionAggregate> foldIntoMonths(List<DailyProductionAggregate> days) {
        Map<YearMonth, ProductionAggregate> months = new HashMap<>();
        for (DailyProductionAggregate day : days) {
            months.merge(YearMonth.from(day.day()),
                    new ProductionAggregate(day.productionKWh(), day.hoursWithData()),
                    (total, next) -> new ProductionAggregate(total.productionKWh() + next.productionKWh(),
                            total.hoursWithData() + next.hoursWithData()));
        }
        return months;
    }
}
