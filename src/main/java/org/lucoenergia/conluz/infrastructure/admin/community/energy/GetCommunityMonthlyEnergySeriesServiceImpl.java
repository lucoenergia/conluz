package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyScope;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyScopeResolver;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergySeriesBucket;
import org.lucoenergia.conluz.domain.admin.community.energy.GetCommunityMonthlyEnergySeriesService;
import org.lucoenergia.conluz.domain.admin.community.energy.SupplyEnergyContribution;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DailyPublishedAggregate;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.MonthlyPublishedAggregate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves the community's supplies and period through the same scope resolver as the community energy
 * metrics, reads every supply's records totalled per local day in a single read, and folds the days
 * into local calendar months.
 *
 * <p>A supply has data in a month when its records carrying self-consumption reach the
 * {@link MonthlyPublishedAggregate#isPublishedWithin publication threshold} of the month's hours inside the period: the
 * criterion the metrics' coverage applies to a period and the daily series to a day. The monthly
 * pre-aggregates are not read, since they keep no count of those records and store a month's missing
 * self-consumption as 0.
 *
 * <h2>Query cost</h2>
 *
 * <p>One relational query (the supplies), then a single InfluxDB query for every supply, grouped by
 * day and supply inside the store: at most 731 rows per supply for the longest period.
 */
@Service
@Transactional(readOnly = true)
public class GetCommunityMonthlyEnergySeriesServiceImpl implements GetCommunityMonthlyEnergySeriesService {

    private final CommunityEnergyScopeResolver communityEnergyScopeResolver;
    private final GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository;

    public GetCommunityMonthlyEnergySeriesServiceImpl(
            CommunityEnergyScopeResolver communityEnergyScopeResolver,
            GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository) {
        this.communityEnergyScopeResolver = communityEnergyScopeResolver;
        this.getDatadisConsumptionAggregateRepository = getDatadisConsumptionAggregateRepository;
    }

    @Override
    public List<CommunityEnergySeriesBucket> getMonthlySeries(UUID communityId, OffsetDateTime startDate,
                                                              OffsetDateTime endDate) {

        CommunityEnergyScope scope = communityEnergyScopeResolver.resolve(communityId, startDate, endDate, null,
                EnergyMetricsPeriodValidator.MAX_LENGTH);

        // Explicit dates always resolve a period, its end clipped to the current hour. A scope without
        // one is a broken invariant, not an empty series.
        return scope.getPeriod()
                .map(period -> getMonthlySeries(scope.getSupplies(), scope.getZone(), period))
                .orElseThrow(() -> new IllegalStateException(
                        "The community monthly energy series resolved a scope from explicit bounds without a "
                        + "period; explicit bounds always resolve one"));
    }

    private List<CommunityEnergySeriesBucket> getMonthlySeries(List<Supply> supplies, ZoneId zone,
                                                               EnergyMetricsPeriod period) {
        Instant from = period.getStartDate().toInstant();
        Instant toExclusive = period.getEndDate().toInstant();
        List<YearMonth> months = period.monthsStartingIn(zone);

        Map<String, List<DailyPublishedAggregate>> daysBySupplyCode =
                getDatadisConsumptionAggregateRepository.aggregateByLocalDay(supplies, from, toExclusive, zone);

        Map<YearMonth, List<SupplyEnergyContribution>> contributionsByMonth = new HashMap<>();
        for (Supply supply : supplies) {
            for (MonthlyPublishedAggregate month : MonthlyPublishedAggregate.foldIntoMonths(
                    daysBySupplyCode.getOrDefault(supply.getCode(), List.of())).values()) {
                // A day before the first selected month belongs to no bucket.
                if (!months.contains(month.month())) {
                    continue;
                }
                if (month.isPublishedWithin(period, zone)) {
                    contributionsByMonth
                            .computeIfAbsent(month.month(), m -> new ArrayList<>())
                            .add(new SupplyEnergyContribution(month.selfConsumptionKWh(), month.surplusKWh()));
                }
            }
        }

        return months.stream()
                .map(month -> CommunityEnergySeriesBucket.of(month.atDay(1).atStartOfDay(zone).toOffsetDateTime(),
                        supplies.size(), contributionsByMonth.getOrDefault(month, List.of())))
                .toList();
    }
}
