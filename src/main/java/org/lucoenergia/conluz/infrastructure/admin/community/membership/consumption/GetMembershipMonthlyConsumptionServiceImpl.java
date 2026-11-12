package org.lucoenergia.conluz.infrastructure.admin.community.membership.consumption;

import org.lucoenergia.conluz.domain.admin.community.membership.consumption.GetMembershipMonthlyConsumptionService;
import org.lucoenergia.conluz.domain.admin.community.membership.consumption.MembershipMonthlyConsumptionBucket;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetricsScope;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetricsScopeResolver;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.SupplyConsumptionBucket;
import org.lucoenergia.conluz.domain.consumption.datadis.get.PublishedMonthlyConsumptionReader;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves the membership and its supplies through the same scope resolver as the aggregated energy
 * metrics, reads every supply's months through the {@link PublishedMonthlyConsumptionReader} -- one
 * read of the hourly records for all of them, keeping each supply's months in which its
 * self-consumption is published -- and folds them month by month. A supply whose self-consumption
 * is not published for a month does not contribute to it: a month before publication is not a
 * measured zero.
 *
 * <p>The months are not taken from the records: every month the range selects is emitted, whether
 * or not any supply has data in it, so the series has a regular time axis.
 *
 * <h2>Query cost</h2>
 *
 * <p>Two relational queries (membership, supplies), then a single InfluxDB query for every supply,
 * and the pricing of each supply's published months, which issues further queries only for a month
 * straddling a tariff change.
 */
@Service
@Transactional(readOnly = true)
public class GetMembershipMonthlyConsumptionServiceImpl implements GetMembershipMonthlyConsumptionService {

    private final MembershipEnergyMetricsScopeResolver membershipEnergyMetricsScopeResolver;
    private final PublishedMonthlyConsumptionReader publishedMonthlyConsumptionReader;
    private final ZoneResolver zoneResolver;

    public GetMembershipMonthlyConsumptionServiceImpl(
            MembershipEnergyMetricsScopeResolver membershipEnergyMetricsScopeResolver,
            PublishedMonthlyConsumptionReader publishedMonthlyConsumptionReader,
            ZoneResolver zoneResolver) {
        this.membershipEnergyMetricsScopeResolver = membershipEnergyMetricsScopeResolver;
        this.publishedMonthlyConsumptionReader = publishedMonthlyConsumptionReader;
        this.zoneResolver = zoneResolver;
    }

    @Override
    public List<MembershipMonthlyConsumptionBucket> getMonthlySeries(UUID communityId, UUID userId,
                                                                     OffsetDateTime startDate,
                                                                     OffsetDateTime endDate) {

        MembershipEnergyMetricsScope scope =
                membershipEnergyMetricsScopeResolver.resolve(communityId, userId, startDate, endDate, null);

        // Explicit dates always resolve a period, its end clipped to the current hour; every supply
        // and the months emitted follow that one period rather than the dates as requested. A scope
        // without one is a broken invariant, not an empty series.
        return scope.getPeriod()
                .map(period -> getMonthlySeries(communityId, scope.getSupplies(), period))
                .orElseThrow(() -> new IllegalStateException(
                        "The membership monthly consumption series resolved a scope from explicit bounds without a "
                        + "period; explicit bounds always resolve one"));
    }

    private List<MembershipMonthlyConsumptionBucket> getMonthlySeries(UUID communityId, List<Supply> supplies,
                                                                      EnergyMetricsPeriod period) {
        ZoneId zone = zoneResolver.resolveZoneIdForCommunity(communityId);

        Map<YearMonth, Map<SupplyId, List<SupplyConsumptionBucket>>> contributionsByMonth = new HashMap<>();
        for (Map.Entry<UUID, List<SupplyConsumptionBucket>> supply : publishedMonthlyConsumptionReader
                .getPublishedMonths(supplies, period, zone).entrySet()) {
            SupplyId supplyId = SupplyId.of(supply.getKey());
            for (SupplyConsumptionBucket bucket : supply.getValue()) {
                contributionsByMonth
                        .computeIfAbsent(monthOf(bucket), month -> new LinkedHashMap<>())
                        .put(supplyId, List.of(bucket));
            }
        }

        return period.monthsStartingIn(zone).stream()
                .map(month -> MembershipMonthlyConsumptionBucket.of(month, supplies.size(),
                        contributionsByMonth.getOrDefault(month, Map.of())))
                .toList();
    }

    /**
     * The local month a supply's bucket belongs to, read from its label: the first day of the month.
     */
    private static YearMonth monthOf(SupplyConsumptionBucket bucket) {
        return YearMonth.from(DateConverter.convertStringToLocalDate(bucket.getConsumption().getDate()));
    }
}
