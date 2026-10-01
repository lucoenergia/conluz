package org.lucoenergia.conluz.infrastructure.admin.community.membership.energymetrics.hourlyprofile;

import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetricsCoverage;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetricsScope;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetricsScopeResolver;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.hourlyprofile.GetMembershipHourlyProfileService;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.hourlyprofile.HourlyProfileAccumulator;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.hourlyprofile.MembershipHourlyProfile;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.SupplyCoverage;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisHourlyRecordsRepository;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the membership's latest published month through the same scope resolver as the
 * aggregated energy metrics, then streams every supply's records over it into one accumulator.
 *
 * <p>Coverage is counted by the same aggregate query the aggregated energy metrics count it with,
 * rather than from the streamed records, so both endpoints report the same figures by construction.
 *
 * <h2>Query cost</h2>
 *
 * <p>The cost of resolving the scope (see
 * {@link org.lucoenergia.conluz.infrastructure.admin.community.membership.energymetrics.MembershipEnergyMetricsScopeResolverImpl}),
 * one zone lookup, then per supply one aggregate query and one streamed read of its records in the
 * month, about 744 of them. Memory stays bounded by the read's chunk and the 24 buckets.
 */
@Service
@Transactional(readOnly = true)
public class GetMembershipHourlyProfileServiceImpl implements GetMembershipHourlyProfileService {

    private final MembershipEnergyMetricsScopeResolver membershipEnergyMetricsScopeResolver;
    private final GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository;
    private final GetDatadisHourlyRecordsRepository getDatadisHourlyRecordsRepository;
    private final ZoneResolver zoneResolver;

    public GetMembershipHourlyProfileServiceImpl(
            MembershipEnergyMetricsScopeResolver membershipEnergyMetricsScopeResolver,
            GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository,
            GetDatadisHourlyRecordsRepository getDatadisHourlyRecordsRepository,
            ZoneResolver zoneResolver) {
        this.membershipEnergyMetricsScopeResolver = membershipEnergyMetricsScopeResolver;
        this.getDatadisConsumptionAggregateRepository = getDatadisConsumptionAggregateRepository;
        this.getDatadisHourlyRecordsRepository = getDatadisHourlyRecordsRepository;
        this.zoneResolver = zoneResolver;
    }

    @Override
    public MembershipHourlyProfile getHourlyProfile(UUID communityId, UUID userId) {

        MembershipEnergyMetricsScope scope =
                membershipEnergyMetricsScopeResolver.resolveLatestPublishedMonth(communityId, userId);

        Optional<EnergyMetricsPeriod> resolved = scope.getPeriod();
        if (resolved.isEmpty()) {
            return MembershipHourlyProfile.withoutPeriod(scope.getSupplies().size());
        }
        EnergyMetricsPeriod period = resolved.get();

        HourlyProfileAccumulator accumulator =
                new HourlyProfileAccumulator(zoneResolver.resolveZoneIdForCommunity(communityId));
        List<SupplyCoverage> coverage = scope.getSupplies().stream()
                .map(supply -> accumulate(supply, period, accumulator))
                .toList();

        return MembershipHourlyProfile.of(period, MembershipEnergyMetricsCoverage.of(coverage),
                accumulator.getBuckets());
    }

    /**
     * Folds the supply's records over the period into the accumulator and reports how much of the
     * period they cover.
     */
    private SupplyCoverage accumulate(Supply supply, EnergyMetricsPeriod period,
                                      HourlyProfileAccumulator accumulator) {
        long hoursWithData = getDatadisConsumptionAggregateRepository
                .aggregateByRangeOfDates(supply, period.getStartDate(), period.getEndDate())
                .getHoursWithData();
        getDatadisHourlyRecordsRepository.forEachRecord(supply, period.getStartDate(), period.getEndDate(),
                accumulator::add);
        return new SupplyCoverage(hoursWithData, period.getExpectedHours());
    }
}
