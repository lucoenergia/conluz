package org.lucoenergia.conluz.infrastructure.admin.community.membership.consumption;

import org.lucoenergia.conluz.domain.admin.community.membership.consumption.GetMembershipMonthlyConsumptionService;
import org.lucoenergia.conluz.domain.admin.community.membership.consumption.MembershipMonthlyConsumptionBucket;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetricsScope;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetricsScopeResolver;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.SupplyConsumptionBucket;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves the membership and its supplies through the same scope resolver as the aggregated energy
 * metrics, reads every supply's monthly series through the per-supply service -- so each supply's
 * month is exactly the bucket the per-supply series reports, energy and savings alike -- and folds
 * them month by month.
 *
 * <p>The months are not taken from the records: every month the range selects is emitted, whether
 * or not any supply stored a record in it, so the series has a regular time axis. The per-supply
 * series omits such months.
 *
 * <h2>Query cost</h2>
 *
 * <p>Two relational queries (membership, supplies), then per supply what one call to the per-supply
 * monthly series costs: one supply lookup, one InfluxDB read of the monthly measurement, and the
 * pricing of each stored month, which issues further queries only for a month straddling a tariff
 * change. It grows with the number of supplies times the number of months.
 */
@Service
@Transactional(readOnly = true)
public class GetMembershipMonthlyConsumptionServiceImpl implements GetMembershipMonthlyConsumptionService {

    private final MembershipEnergyMetricsScopeResolver membershipEnergyMetricsScopeResolver;
    private final GetDatadisConsumptionService getDatadisConsumptionService;
    private final ZoneResolver zoneResolver;

    public GetMembershipMonthlyConsumptionServiceImpl(
            MembershipEnergyMetricsScopeResolver membershipEnergyMetricsScopeResolver,
            GetDatadisConsumptionService getDatadisConsumptionService,
            ZoneResolver zoneResolver) {
        this.membershipEnergyMetricsScopeResolver = membershipEnergyMetricsScopeResolver;
        this.getDatadisConsumptionService = getDatadisConsumptionService;
        this.zoneResolver = zoneResolver;
    }

    @Override
    public List<MembershipMonthlyConsumptionBucket> getMonthlySeries(UUID communityId, UUID userId,
                                                                     OffsetDateTime startDate,
                                                                     OffsetDateTime endDate) {

        MembershipEnergyMetricsScope scope =
                membershipEnergyMetricsScopeResolver.resolve(communityId, userId, startDate, endDate, null);
        List<Supply> supplies = scope.getSupplies();

        Map<YearMonth, Map<SupplyId, List<SupplyConsumptionBucket>>> contributionsByMonth = new HashMap<>();
        for (Supply supply : supplies) {
            SupplyId supplyId = SupplyId.of(supply.getId());
            for (SupplyConsumptionBucket bucket :
                    getDatadisConsumptionService.getMonthlySeriesBySupply(supplyId, startDate, endDate)) {
                contributionsByMonth
                        .computeIfAbsent(monthOf(bucket), month -> new LinkedHashMap<>())
                        .computeIfAbsent(supplyId, id -> new ArrayList<>())
                        .add(bucket);
            }
        }

        ZoneId zone = zoneResolver.resolveZoneIdForCommunity(communityId);
        return monthsSelectedBy(startDate, endDate, zone).stream()
                .map(month -> MembershipMonthlyConsumptionBucket.of(month, supplies.size(),
                        contributionsByMonth.getOrDefault(month, Map.of())))
                .toList();
    }

    /**
     * The local month a per-supply bucket belongs to, read from its label exactly as the per-supply
     * series reads it to price the bucket.
     */
    private static YearMonth monthOf(SupplyConsumptionBucket bucket) {
        return YearMonth.from(DateConverter.convertStringToLocalDate(bucket.getConsumption().getDate()));
    }

    /**
     * Every month whose local midnight on day 1 falls inside the inclusive bounds, in chronological
     * order: the rule by which the per-supply series selects the pre-aggregated point stamped at
     * that instant.
     */
    private static List<YearMonth> monthsSelectedBy(OffsetDateTime startDate, OffsetDateTime endDate, ZoneId zone) {
        Instant from = startDate.toInstant();
        Instant to = endDate.toInstant();
        YearMonth month = YearMonth.from(startDate.atZoneSameInstant(zone));
        if (startOf(month, zone).isBefore(from)) {
            month = month.plusMonths(1);
        }
        List<YearMonth> months = new ArrayList<>();
        while (!startOf(month, zone).isAfter(to)) {
            months.add(month);
            month = month.plusMonths(1);
        }
        return months;
    }

    private static Instant startOf(YearMonth month, ZoneId zone) {
        return month.atDay(1).atStartOfDay(zone).toInstant();
    }
}
