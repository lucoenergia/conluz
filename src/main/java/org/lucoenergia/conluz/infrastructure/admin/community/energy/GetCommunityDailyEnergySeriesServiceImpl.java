package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyScope;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyScopeResolver;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergySeriesBucket;
import org.lucoenergia.conluz.domain.admin.community.energy.GetCommunityDailyEnergySeriesService;
import org.lucoenergia.conluz.domain.admin.community.energy.SupplyEnergyContribution;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.consumption.PublicationThreshold;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DailyPublishedAggregate;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves the community's supplies and period through the same scope resolver as the community energy
 * metrics, totals every supply's records per local day, and folds them day by day.
 *
 * <p>A supply has data on a day when its records carrying self-consumption reach the
 * {@link PublicationThreshold publication threshold} of the day's hours inside the period: the
 * criterion a month is published by, applied to the day. A day the period starts or ends inside counts
 * only its hours inside the period.
 *
 * <h2>Query cost</h2>
 *
 * <p>One relational query (the supplies), then one InfluxDB query per supply, grouped by day inside
 * the store.
 */
@Service
@Transactional(readOnly = true)
public class GetCommunityDailyEnergySeriesServiceImpl implements GetCommunityDailyEnergySeriesService {

    private final CommunityEnergyScopeResolver communityEnergyScopeResolver;
    private final GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository;

    public GetCommunityDailyEnergySeriesServiceImpl(
            CommunityEnergyScopeResolver communityEnergyScopeResolver,
            GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository) {
        this.communityEnergyScopeResolver = communityEnergyScopeResolver;
        this.getDatadisConsumptionAggregateRepository = getDatadisConsumptionAggregateRepository;
    }

    @Override
    public List<CommunityEnergySeriesBucket> getDailySeries(UUID communityId, OffsetDateTime startDate,
                                                            OffsetDateTime endDate) {

        CommunityEnergyScope scope = communityEnergyScopeResolver.resolve(communityId, startDate, endDate, null,
                EnergyMetricsPeriodValidator.MAX_LENGTH);

        // Explicit dates always resolve a period, its end clipped to the current hour. A scope without
        // one is a broken invariant, not an empty series.
        return scope.getPeriod()
                .map(period -> getDailySeries(scope.getSupplies(), scope.getZone(), period))
                .orElseThrow(() -> new IllegalStateException(
                        "The community daily energy series resolved a scope from explicit bounds without a "
                        + "period; explicit bounds always resolve one"));
    }

    private List<CommunityEnergySeriesBucket> getDailySeries(List<Supply> supplies, ZoneId zone,
                                                             EnergyMetricsPeriod period) {
        Instant from = period.getStartDate().toInstant();
        Instant toExclusive = period.getEndDate().toInstant();

        Map<LocalDate, List<SupplyEnergyContribution>> contributionsByDay = new HashMap<>();
        for (Supply supply : supplies) {
            for (DailyPublishedAggregate day : getDatadisConsumptionAggregateRepository.aggregateByLocalDay(
                    supply, from, toExclusive, zone)) {
                long possibleHours = period.hoursOverlapping(EnergyMetricsPeriod.ofDay(day.day(), zone));
                // A day too short to hold a whole hour holds no record, and no supply has data in it.
                if (day.publishedHours() > 0
                        && PublicationThreshold.isReached(day.publishedHours(), possibleHours)) {
                    contributionsByDay
                            .computeIfAbsent(day.day(), d -> new ArrayList<>())
                            .add(new SupplyEnergyContribution(day.selfConsumptionKWh(), day.surplusKWh()));
                }
            }
        }

        return period.daysOverlapping(zone).stream()
                .map(day -> CommunityEnergySeriesBucket.of(day.atStartOfDay(zone).toOffsetDateTime(),
                        supplies.size(), contributionsByDay.getOrDefault(day, List.of())))
                .toList();
    }
}
