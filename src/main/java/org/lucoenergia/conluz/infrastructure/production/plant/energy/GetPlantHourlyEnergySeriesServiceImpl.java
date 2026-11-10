package org.lucoenergia.conluz.infrastructure.production.plant.energy;

import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.production.datadis.get.GetDatadisProductionAggregateRepository;
import org.lucoenergia.conluz.domain.production.datadis.get.ProductionAggregate;
import org.lucoenergia.conluz.domain.production.plant.energy.GetPlantHourlyEnergySeriesService;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyScope;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyScopeResolver;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergySeriesBucket;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the plant's backing supply and period through the same scope resolver as the plant energy
 * metrics, reads the supply's hourly production records, and emits one bucket per hour starting inside
 * the period: the hour's record, or a gap.
 *
 * <h2>Query cost</h2>
 *
 * <p>One relational query (the plant), then a single InfluxDB query: at most 745 records for the
 * longest period.
 */
@Service
@Transactional(readOnly = true)
public class GetPlantHourlyEnergySeriesServiceImpl implements GetPlantHourlyEnergySeriesService {

    private final PlantEnergyScopeResolver plantEnergyScopeResolver;
    private final GetDatadisProductionAggregateRepository getDatadisProductionAggregateRepository;

    public GetPlantHourlyEnergySeriesServiceImpl(
            PlantEnergyScopeResolver plantEnergyScopeResolver,
            GetDatadisProductionAggregateRepository getDatadisProductionAggregateRepository) {
        this.plantEnergyScopeResolver = plantEnergyScopeResolver;
        this.getDatadisProductionAggregateRepository = getDatadisProductionAggregateRepository;
    }

    @Override
    public List<PlantEnergySeriesBucket> getHourlySeries(UUID plantId, OffsetDateTime startDate,
                                                         OffsetDateTime endDate) {

        PlantEnergyScope scope = plantEnergyScopeResolver.resolve(plantId, startDate, endDate, null,
                EnergyMetricsPeriodValidator.MAX_HOURLY_LENGTH);

        // Explicit dates always resolve a period, its end clipped to the current hour. A scope without
        // one is a broken invariant, not an empty series.
        return scope.getPeriod()
                .map(period -> getHourlySeries(scope.getSupplyCode(), scope.getZone(), period))
                .orElseThrow(() -> new IllegalStateException(
                        "The plant hourly energy series resolved a scope from explicit bounds without a "
                        + "period; explicit bounds always resolve one"));
    }

    private List<PlantEnergySeriesBucket> getHourlySeries(String supplyCode, ZoneId zone,
                                                          EnergyMetricsPeriod period) {
        Map<Instant, Double> production = getDatadisProductionAggregateRepository.findHourlyProduction(
                supplyCode, period.getStartDate().toInstant(), period.getEndDate().toInstant());

        return period.hoursStartingIn(zone).stream()
                .map(hour -> PlantEnergySeriesBucket.of(hour.atZone(zone).toOffsetDateTime(), 1,
                        Optional.ofNullable(production.get(hour)).map(kWh -> new ProductionAggregate(kWh, 1))))
                .toList();
    }
}
