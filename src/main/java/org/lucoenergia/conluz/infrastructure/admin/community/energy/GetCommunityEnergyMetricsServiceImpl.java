package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyMetrics;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyScope;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyScopeResolver;
import org.lucoenergia.conluz.domain.admin.community.energy.GetCommunityEnergyMetricsService;
import org.lucoenergia.conluz.domain.admin.community.energy.SurplusIncome;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsReferencePeriod;
import org.lucoenergia.conluz.domain.consumption.PublicationThreshold;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DatadisConsumptionAggregate;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.production.datadis.get.GetDatadisProductionAggregateRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.price.get.GetPriceRepository;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Resolves one period for the whole community, totals every supply in scope over it, counts the
 * supplies with data, and reads every plant's production.
 *
 * <p>A supply has data for the period when its records carrying self-consumption reach the
 * {@link PublicationThreshold publication threshold} of the hours the period spans: the criterion a
 * month is published by, applied to the period requested.
 *
 * <p>The surplus is also priced hour by hour at the mean OMIE day-ahead price of each hour, over the
 * same supplies and the same period as its total.
 *
 * <h2>Query cost</h2>
 *
 * <p>The cost of resolving the scope (see {@link CommunityEnergyScopeResolverImpl}) and one relational
 * query for the plants, then two Influx queries per supply (its totals and its published hours) and a
 * single one for the production of every plant. Two more Influx queries, the hours with surplus of every
 * supply summed per hour and the mean price of every hour, start on the application task executor as
 * soon as the period is resolved, so they run while the supplies are totalled; their results are
 * discarded when no supply stored a record. Every sum and mean is pushed down to the store, so the
 * number of queries grows with the number of supplies, not with the length of the period.
 */
@Service
@Transactional(readOnly = true)
public class GetCommunityEnergyMetricsServiceImpl implements GetCommunityEnergyMetricsService {

    private final CommunityEnergyScopeResolver communityEnergyScopeResolver;
    private final GetPlantRepository getPlantRepository;
    private final GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository;
    private final GetDatadisProductionAggregateRepository getDatadisProductionAggregateRepository;
    private final GetPriceRepository getPriceRepository;
    private final TaskExecutor taskExecutor;

    public GetCommunityEnergyMetricsServiceImpl(
            CommunityEnergyScopeResolver communityEnergyScopeResolver,
            GetPlantRepository getPlantRepository,
            GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository,
            GetDatadisProductionAggregateRepository getDatadisProductionAggregateRepository,
            GetPriceRepository getPriceRepository,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.communityEnergyScopeResolver = communityEnergyScopeResolver;
        this.getPlantRepository = getPlantRepository;
        this.getDatadisConsumptionAggregateRepository = getDatadisConsumptionAggregateRepository;
        this.getDatadisProductionAggregateRepository = getDatadisProductionAggregateRepository;
        this.getPriceRepository = getPriceRepository;
        this.taskExecutor = taskExecutor;
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
        ZoneId zone = scope.getZone();

        // Started before the totals, which decide whether they are needed, so the reads overlap them; when
        // no supply stored a record their results are discarded. The surplus is read over the same supplies
        // and interval as its total, and the prices over whole hours, so an hour the interval's bounds cut
        // through is still priced at the mean of all its prices.
        CompletableFuture<Map<Instant, Double>> surplusByHour = CompletableFuture.supplyAsync(
                () -> getDatadisConsumptionAggregateRepository.sumSurplusByHour(supplies, from, toExclusive, zone),
                taskExecutor);
        CompletableFuture<Map<Instant, Double>> meanPriceByHour = CompletableFuture.supplyAsync(
                () -> getPriceRepository.meanPriceByHour(startOfHour(from, zone), endOfLastHour(toExclusive, zone),
                        zone),
                taskExecutor);

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
                anyRecord ? SurplusIncome.estimate(await(surplusByHour), await(meanPriceByHour)) : null,
                supplies.size(), suppliesWithData, plants, productionBySupplyCode);
    }

    /**
     * The read's result, or the exception it failed with, unwrapped, so a failed read is answered as it
     * would be on the request's own thread.
     */
    private static <T> T await(CompletableFuture<T> read) {
        try {
            return read.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            if (e.getCause() instanceof Error cause) {
                throw cause;
            }
            throw e;
        }
    }

    private static Instant startOfHour(Instant instant, ZoneId zone) {
        return instant.atZone(zone).truncatedTo(ChronoUnit.HOURS).toInstant();
    }

    /**
     * The end of the hour that holds the last instant before {@code toExclusive}.
     */
    private static Instant endOfLastHour(Instant toExclusive, ZoneId zone) {
        return startOfHour(toExclusive.minusNanos(1), zone).plus(1, ChronoUnit.HOURS);
    }
}
