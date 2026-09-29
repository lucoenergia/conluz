package org.lucoenergia.conluz.infrastructure.admin.community.membership.energymetrics;

import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.GetMembershipEnergyMetricsService;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetrics;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetricsScope;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetricsScopeResolver;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsReferencePeriod;
import org.lucoenergia.conluz.domain.consumption.GetSupplyEnergyMetricsService;
import org.lucoenergia.conluz.domain.consumption.SupplyEnergyMetrics;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves one period for the whole membership, computes every supply's metrics over it through
 * the per-supply metrics service, and adds them up.
 *
 * <p>Every supply is computed over the <em>same</em> period, even when the period is derived from
 * the stored records, so the expected hours of a supply with a shorter history still count against
 * coverage instead of silently shrinking the period.
 *
 * <h2>Query cost</h2>
 *
 * <p>The cost of resolving the scope (see {@link MembershipEnergyMetricsScopeResolverImpl}), then
 * per supply: one supply lookup, one aggregate query and one pricing query per tariff segment. The
 * cost grows with the number of supplies, not with the length of the period, since every sum is
 * pushed down to the store.
 */
@Service
@Transactional(readOnly = true)
public class GetMembershipEnergyMetricsServiceImpl implements GetMembershipEnergyMetricsService {

    private final MembershipEnergyMetricsScopeResolver membershipEnergyMetricsScopeResolver;
    private final GetSupplyEnergyMetricsService getSupplyEnergyMetricsService;

    public GetMembershipEnergyMetricsServiceImpl(
            MembershipEnergyMetricsScopeResolver membershipEnergyMetricsScopeResolver,
            GetSupplyEnergyMetricsService getSupplyEnergyMetricsService) {
        this.membershipEnergyMetricsScopeResolver = membershipEnergyMetricsScopeResolver;
        this.getSupplyEnergyMetricsService = getSupplyEnergyMetricsService;
    }

    @Override
    public MembershipEnergyMetrics getEnergyMetrics(UUID communityId, UUID userId, OffsetDateTime startDate,
                                                    OffsetDateTime endDate,
                                                    EnergyMetricsReferencePeriod referencePeriod) {

        MembershipEnergyMetricsScope scope = membershipEnergyMetricsScopeResolver.resolve(
                communityId, userId, startDate, endDate, referencePeriod);

        Optional<EnergyMetricsPeriod> period = scope.getPeriod();
        if (period.isEmpty()) {
            return MembershipEnergyMetrics.withoutPeriod(scope.getSupplies().size());
        }

        OffsetDateTime resolvedStartDate = period.get().getStartDate();
        OffsetDateTime resolvedEndDate = period.get().getEndDate();
        List<SupplyEnergyMetrics> metrics = scope.getSupplies().stream()
                .map(supply -> getSupplyEnergyMetricsService.getEnergyMetrics(
                        SupplyId.of(supply.getId()), resolvedStartDate, resolvedEndDate))
                .toList();

        return MembershipEnergyMetrics.of(resolvedStartDate, resolvedEndDate, metrics);
    }
}
