package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyMetrics;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyScope;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyScopeResolver;
import org.lucoenergia.conluz.domain.admin.community.energy.GetCommunityEnergyMetricsService;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsReferencePeriod;
import org.lucoenergia.conluz.domain.consumption.PublicationThreshold;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DatadisConsumptionAggregate;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.production.datadis.get.GetDatadisProductionAggregateRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves one period for the whole community, totals every supply in scope over it, counts the
 * supplies with data, and reads every plant's production.
 *
 * <p>A supply has data for the period when its records carrying self-consumption reach the
 * {@link PublicationThreshold publication threshold} of the hours the period spans: the criterion a
 * month is published by, applied to the period requested.
 *
 * <h2>Query cost</h2>
 *
 * <p>The cost of resolving the scope (see {@link CommunityEnergyScopeResolverImpl}) and one relational
 * query for the plants, then two Influx queries per supply (its totals and its published hours) and a
 * single one for the production of every plant. Every sum is pushed down to the store, so the cost
 * grows with the number of supplies, not with the length of the period.
 */
@Service
@Transactional(readOnly = true)
public class GetCommunityEnergyMetricsServiceImpl implements GetCommunityEnergyMetricsService {

    private final CommunityEnergyScopeResolver communityEnergyScopeResolver;
    private final GetPlantRepository getPlantRepository;
    private final GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository;
    private final GetDatadisProductionAggregateRepository getDatadisProductionAggregateRepository;

    public GetCommunityEnergyMetricsServiceImpl(
            CommunityEnergyScopeResolver communityEnergyScopeResolver,
            GetPlantRepository getPlantRepository,
            GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository,
            GetDatadisProductionAggregateRepository getDatadisProductionAggregateRepository) {
        this.communityEnergyScopeResolver = communityEnergyScopeResolver;
        this.getPlantRepository = getPlantRepository;
        this.getDatadisConsumptionAggregateRepository = getDatadisConsumptionAggregateRepository;
        this.getDatadisProductionAggregateRepository = getDatadisProductionAggregateRepository;
    }

    @Override
    public CommunityEnergyMetrics getEnergyMetrics(UUID communityId, OffsetDateTime startDate,
                                                   OffsetDateTime endDate,
                                                   EnergyMetricsReferencePeriod referencePeriod) {

        CommunityEnergyScope scope = communityEnergyScopeResolver.resolve(communityId, startDate, endDate,
                referencePeriod, EnergyMetricsPeriodValidator.MAX_LENGTH);
        List<Supply> supplies = scope.getSupplies();
        List<Plant> plants = getPlantRepository.findAllByCommunityId(communityId);

        Optional<EnergyMetricsPeriod> resolved = scope.getPeriod();
        if (resolved.isEmpty()) {
            return CommunityEnergyMetrics.withoutPeriod(supplies.size(), plants);
        }
        EnergyMetricsPeriod period = resolved.get();
        Instant from = period.getStartDate().toInstant();
        Instant toExclusive = period.getEndDate().toInstant();
        long possibleHours = period.getExpectedHours();

        double selfConsumptionKWh = 0d;
        double surplusKWh = 0d;
        long hoursWithData = 0L;
        int suppliesWithData = 0;
        for (Supply supply : supplies) {
            DatadisConsumptionAggregate aggregate = getDatadisConsumptionAggregateRepository
                    .aggregateByRangeOfDates(supply, period.getStartDate(), period.getEndDate());
            selfConsumptionKWh += aggregate.getSelfConsumptionEnergyKWh();
            surplusKWh += aggregate.getSurplusEnergyKWh();
            hoursWithData += aggregate.getHoursWithData();

            long publishedHours = getDatadisConsumptionAggregateRepository.countPublishedHours(supply, from,
                    toExclusive);
            // A period too short to hold a whole hour holds no record, and no supply has data in it.
            if (publishedHours > 0 && PublicationThreshold.isReached(publishedHours, possibleHours)) {
                suppliesWithData++;
            }
        }

        Map<String, Double> productionBySupplyCode = getDatadisProductionAggregateRepository.sumProductionByCups(
                plants.stream().map(plant -> plant.getSupply().getCode()).toList(), from, toExclusive);

        // No supply stored a record: a gap, not a measured zero.
        boolean anyRecord = hoursWithData > 0;
        return CommunityEnergyMetrics.of(period,
                anyRecord ? selfConsumptionKWh : null,
                anyRecord ? surplusKWh : null,
                supplies.size(), suppliesWithData, plants, productionBySupplyCode);
    }
}
