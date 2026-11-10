package org.lucoenergia.conluz.infrastructure.production.plant.energy;

import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.production.datadis.get.GetDatadisProductionAggregateRepository;
import org.lucoenergia.conluz.domain.production.plant.energy.GetPlantEnergyMetricsService;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyMetrics;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyMetricsReferencePeriod;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyScope;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyScopeResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resolves the plant's backing supply and period, then totals the supply's hourly production records
 * over the period. The monthly and yearly pre-aggregates are not read.
 *
 * <h2>Query cost</h2>
 *
 * <p>What {@link PlantEnergyScopeResolver} costs, then a single InfluxDB query for the totals, skipped
 * when no period was resolved: at most two InfluxDB requests.
 */
@Service
@Transactional(readOnly = true)
public class GetPlantEnergyMetricsServiceImpl implements GetPlantEnergyMetricsService {

    private final PlantEnergyScopeResolver plantEnergyScopeResolver;
    private final GetDatadisProductionAggregateRepository getDatadisProductionAggregateRepository;

    public GetPlantEnergyMetricsServiceImpl(PlantEnergyScopeResolver plantEnergyScopeResolver,
                                            GetDatadisProductionAggregateRepository getDatadisProductionAggregateRepository) {
        this.plantEnergyScopeResolver = plantEnergyScopeResolver;
        this.getDatadisProductionAggregateRepository = getDatadisProductionAggregateRepository;
    }

    @Override
    public PlantEnergyMetrics getEnergyMetrics(UUID plantId, OffsetDateTime startDate, OffsetDateTime endDate,
                                               PlantEnergyMetricsReferencePeriod referencePeriod) {

        PlantEnergyScope scope = plantEnergyScopeResolver.resolve(plantId, startDate, endDate, referencePeriod,
                EnergyMetricsPeriodValidator.MAX_LENGTH);

        return scope.getPeriod()
                .map(period -> PlantEnergyMetrics.of(period, getDatadisProductionAggregateRepository.aggregate(
                        scope.getSupplyCode(), period.getStartDate().toInstant(), period.getEndDate().toInstant())))
                .orElseGet(PlantEnergyMetrics::withoutPeriod);
    }
}
