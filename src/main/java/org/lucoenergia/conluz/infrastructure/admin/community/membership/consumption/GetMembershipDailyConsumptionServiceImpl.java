package org.lucoenergia.conluz.infrastructure.admin.community.membership.consumption;

import org.lucoenergia.conluz.domain.admin.community.membership.consumption.GetMembershipDailyConsumptionService;
import org.lucoenergia.conluz.domain.admin.community.membership.consumption.MembershipDailyConsumptionBucket;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetricsScope;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetricsScopeResolver;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.SupplyConsumptionBucket;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves the membership and its supplies through the same scope resolver as the aggregated energy
 * metrics and the membership monthly series, reads every supply's daily series through the
 * per-supply service -- so each supply's day is exactly the bucket the per-supply series reports,
 * edge days clipped, energy and savings alike -- and folds them day by day.
 *
 * <p>A supply contributes to a day only when it stored at least one record that day. The per-supply
 * series emits every day, with zeros where nothing was stored, and tells the two apart only by
 * {@code obtainMethod}: it is the last stored value of the day, and every stored hourly record
 * carries one, so it is null exactly when the supply stored nothing that day.
 *
 * <p>The days are not taken from the records: every local day of the community's time zone that
 * overlaps the period is emitted. The per-supply series labels its days in the supply's zone; both
 * zones resolve to the application zone today, so a supply's day and the membership's day are the
 * same date.
 *
 * <h2>Query cost</h2>
 *
 * <p>Two relational queries (membership, supplies), then per supply what one call to the per-supply
 * daily series costs: one supply lookup, one InfluxDB read of the hourly measurement grouped by day,
 * and the pricing of each day, which issues further queries only for a day straddling a tariff
 * change. It grows with the number of supplies times the number of days, never with a query per
 * day.
 */
@Service
@Transactional(readOnly = true)
public class GetMembershipDailyConsumptionServiceImpl implements GetMembershipDailyConsumptionService {

    private final MembershipEnergyMetricsScopeResolver membershipEnergyMetricsScopeResolver;
    private final GetDatadisConsumptionService getDatadisConsumptionService;
    private final ZoneResolver zoneResolver;

    public GetMembershipDailyConsumptionServiceImpl(
            MembershipEnergyMetricsScopeResolver membershipEnergyMetricsScopeResolver,
            GetDatadisConsumptionService getDatadisConsumptionService,
            ZoneResolver zoneResolver) {
        this.membershipEnergyMetricsScopeResolver = membershipEnergyMetricsScopeResolver;
        this.getDatadisConsumptionService = getDatadisConsumptionService;
        this.zoneResolver = zoneResolver;
    }

    @Override
    public List<MembershipDailyConsumptionBucket> getDailySeries(UUID communityId, UUID userId,
                                                                 OffsetDateTime startDate,
                                                                 OffsetDateTime endDate) {

        MembershipEnergyMetricsScope scope =
                membershipEnergyMetricsScopeResolver.resolve(communityId, userId, startDate, endDate, null);

        // Explicit dates always resolve a period, its end clipped to the current hour; every supply
        // and the days emitted follow that one period rather than the dates as requested.
        return scope.getPeriod()
                .map(period -> getDailySeries(communityId, scope.getSupplies(), period))
                .orElse(List.of());
    }

    private List<MembershipDailyConsumptionBucket> getDailySeries(UUID communityId, List<Supply> supplies,
                                                                  EnergyMetricsPeriod period) {
        Map<LocalDate, Map<SupplyId, List<SupplyConsumptionBucket>>> contributionsByDay = new HashMap<>();
        for (Supply supply : supplies) {
            SupplyId supplyId = SupplyId.of(supply.getId());
            for (SupplyConsumptionBucket bucket : getDatadisConsumptionService.getDailySeriesBySupply(
                    supplyId, period.getStartDate(), period.getEndDate())) {
                if (!storedARecord(bucket)) {
                    continue;
                }
                contributionsByDay
                        .computeIfAbsent(dayOf(bucket), day -> new LinkedHashMap<>())
                        .computeIfAbsent(supplyId, id -> new ArrayList<>())
                        .add(bucket);
            }
        }

        ZoneId zone = zoneResolver.resolveZoneIdForCommunity(communityId);
        return daysOverlapping(period.getStartDate(), period.getEndDate(), zone).stream()
                .map(day -> MembershipDailyConsumptionBucket.of(day, supplies.size(),
                        contributionsByDay.getOrDefault(day, Map.of())))
                .toList();
    }

    /**
     * Whether the supply stored at least one record that day: the only signal the per-supply series
     * gives, since it reports a day with nothing stored as zeros.
     */
    private static boolean storedARecord(SupplyConsumptionBucket bucket) {
        return bucket.getConsumption().getObtainMethod() != null;
    }

    /**
     * The local day a per-supply bucket belongs to, read from its label exactly as the per-supply
     * series reads it to price the bucket.
     */
    private static LocalDate dayOf(SupplyConsumptionBucket bucket) {
        return DateConverter.convertStringToLocalDate(bucket.getConsumption().getDate());
    }

    /**
     * Every local day that overlaps the half-open bounds, in chronological order: from the day the
     * start falls on to the day of the last instant before the end. The period rules guarantee the
     * start is before the end, so there is at least one.
     */
    private static List<LocalDate> daysOverlapping(OffsetDateTime startDate, OffsetDateTime endDate, ZoneId zone) {
        LocalDate first = startDate.atZoneSameInstant(zone).toLocalDate();
        LocalDate last = endDate.minusNanos(1).atZoneSameInstant(zone).toLocalDate();
        return first.datesUntil(last.plusDays(1)).toList();
    }
}
