package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyScope;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyScopeResolver;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergySeriesBucket;
import org.lucoenergia.conluz.domain.admin.community.energy.GetCommunityMonthlyEnergySeriesService;
import org.lucoenergia.conluz.domain.admin.community.energy.SupplyEnergyContribution;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * metrics, reads every supply's monthly pre-aggregates, as the membership monthly series does, and
 * folds them month by month.
 *
 * <p>A supply has data in a month when it has a stored monthly point for it. The pre-aggregate keeps no
 * count of the hours that carry self-consumption, and stores self-consumption a month's records lack
 * as 0, so the publication threshold the daily and hourly series apply cannot be applied here: a month
 * aggregated before the distributor published it reads as a measured zero.
 *
 * <h2>Query cost</h2>
 *
 * <p>One relational query (the supplies), then one InfluxDB read of the monthly measurement per supply.
 */
@Service
@Transactional(readOnly = true)
public class GetCommunityMonthlyEnergySeriesServiceImpl implements GetCommunityMonthlyEnergySeriesService {

    private final CommunityEnergyScopeResolver communityEnergyScopeResolver;
    private final GetDatadisConsumptionRepository getDatadisConsumptionRepository;

    public GetCommunityMonthlyEnergySeriesServiceImpl(
            CommunityEnergyScopeResolver communityEnergyScopeResolver,
            @Qualifier("getDatadisConsumptionRepositoryInflux") GetDatadisConsumptionRepository getDatadisConsumptionRepository) {
        this.communityEnergyScopeResolver = communityEnergyScopeResolver;
        this.getDatadisConsumptionRepository = getDatadisConsumptionRepository;
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
        Map<YearMonth, List<SupplyEnergyContribution>> contributionsByMonth = new HashMap<>();
        for (Supply supply : supplies) {
            for (DatadisConsumption month : getDatadisConsumptionRepository.getMonthlyConsumptionsByRangeOfDates(
                    supply, period.getStartDate(), period.getEndDate())) {
                contributionsByMonth
                        .computeIfAbsent(YearMonth.from(DateConverter.convertStringToLocalDate(month.getDate())),
                                m -> new ArrayList<>())
                        .add(new SupplyEnergyContribution(valueOf(month.getSelfConsumptionEnergyKWh()),
                                valueOf(month.getSurplusEnergyKWh())));
            }
        }

        return period.monthsStartingIn(zone).stream()
                .map(month -> CommunityEnergySeriesBucket.of(month.atDay(1).atStartOfDay(zone).toOffsetDateTime(),
                        supplies.size(), contributionsByMonth.getOrDefault(month, List.of())))
                .toList();
    }

    private static double valueOf(Float kWh) {
        return kWh == null ? 0d : kWh;
    }
}
