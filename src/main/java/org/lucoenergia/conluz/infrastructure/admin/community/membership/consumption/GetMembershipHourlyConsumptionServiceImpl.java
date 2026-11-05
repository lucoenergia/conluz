package org.lucoenergia.conluz.infrastructure.admin.community.membership.consumption;

import org.lucoenergia.conluz.domain.admin.community.membership.consumption.GetMembershipHourlyConsumptionService;
import org.lucoenergia.conluz.domain.admin.community.membership.consumption.MembershipHourlyConsumptionBucket;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetricsScope;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetricsScopeResolver;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.consumption.SupplyConsumptionBucket;
import org.lucoenergia.conluz.domain.consumption.SupplyHourlyConsumptionBucket;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves the membership and its supplies through the same scope resolver as the membership daily
 * series, held to the hourly length limit, reads every supply's hourly series through the per-supply
 * service -- so each supply's hour is exactly the bucket the per-supply series reports, energy and
 * savings alike -- and folds them hour by hour.
 *
 * <p>A supply contributes to an hour only when it stored a record that hour. The per-supply series
 * emits every hour, with zeros and no {@code obtainMethod} where nothing was stored, and every stored
 * record carries one, so it is null exactly when the supply stored nothing that hour.
 *
 * <p>The hours are not taken from the records: every whole hour of the community's time zone that
 * starts inside the period is emitted. They are matched to the per-supply hours by the instant they
 * start at, never by their local date and time, which two hours share on the day the clocks go back.
 *
 * <h2>Query cost</h2>
 *
 * <p>Two relational queries (membership, supplies), then per supply what one call to the per-supply
 * hourly series costs: one supply lookup, one InfluxDB read of the hourly measurement, and the
 * pricing of each hour, which issues further queries only for an hour straddling a tariff change. It
 * grows with the number of supplies times the number of hours, never with a query per hour.
 */
@Service
@Transactional(readOnly = true)
public class GetMembershipHourlyConsumptionServiceImpl implements GetMembershipHourlyConsumptionService {

    private final MembershipEnergyMetricsScopeResolver membershipEnergyMetricsScopeResolver;
    private final GetDatadisConsumptionService getDatadisConsumptionService;
    private final ZoneResolver zoneResolver;

    public GetMembershipHourlyConsumptionServiceImpl(
            MembershipEnergyMetricsScopeResolver membershipEnergyMetricsScopeResolver,
            GetDatadisConsumptionService getDatadisConsumptionService,
            ZoneResolver zoneResolver) {
        this.membershipEnergyMetricsScopeResolver = membershipEnergyMetricsScopeResolver;
        this.getDatadisConsumptionService = getDatadisConsumptionService;
        this.zoneResolver = zoneResolver;
    }

    @Override
    public List<MembershipHourlyConsumptionBucket> getHourlySeries(UUID communityId, UUID userId,
                                                                   OffsetDateTime startDate,
                                                                   OffsetDateTime endDate) {

        MembershipEnergyMetricsScope scope = membershipEnergyMetricsScopeResolver.resolve(communityId, userId,
                startDate, endDate, null, EnergyMetricsPeriodValidator.MAX_HOURLY_LENGTH);

        // Explicit dates always resolve a period, its end clipped to the current hour; every supply
        // and the hours emitted follow that one period rather than the dates as requested. A scope
        // without one is a broken invariant, not an empty series.
        return scope.getPeriod()
                .map(period -> getHourlySeries(communityId, scope.getSupplies(), period))
                .orElseThrow(() -> new IllegalStateException(
                        "The membership hourly consumption series resolved a scope from explicit bounds without a "
                        + "period; explicit bounds always resolve one"));
    }

    private List<MembershipHourlyConsumptionBucket> getHourlySeries(UUID communityId, List<Supply> supplies,
                                                                    EnergyMetricsPeriod period) {
        Map<Instant, Map<SupplyId, List<SupplyConsumptionBucket>>> contributionsByHour = new HashMap<>();
        for (Supply supply : supplies) {
            SupplyId supplyId = SupplyId.of(supply.getId());
            for (SupplyHourlyConsumptionBucket hour : getDatadisConsumptionService.getHourlySeriesBySupply(
                    supplyId, period.getStartDate(), period.getEndDate())) {
                if (!storedARecord(hour.getBucket())) {
                    continue;
                }
                contributionsByHour
                        .computeIfAbsent(hour.getStartsAt().toInstant(), instant -> new LinkedHashMap<>())
                        .computeIfAbsent(supplyId, id -> new ArrayList<>())
                        .add(hour.getBucket());
            }
        }

        ZoneId zone = zoneResolver.resolveZoneIdForCommunity(communityId);
        return period.hoursStartingIn(zone).stream()
                .map(hour -> MembershipHourlyConsumptionBucket.of(hour.atZone(zone).toOffsetDateTime(),
                        supplies.size(), contributionsByHour.getOrDefault(hour, Map.of())))
                .toList();
    }

    /**
     * Whether the supply stored a record that hour: the only signal the per-supply series gives,
     * since it reports an hour with nothing stored as zeros.
     */
    private static boolean storedARecord(SupplyConsumptionBucket bucket) {
        return bucket.getConsumption().getObtainMethod() != null;
    }
}
