package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyScope;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyScopeResolver;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergySeriesBucket;
import org.lucoenergia.conluz.domain.admin.community.energy.GetCommunityHourlyEnergySeriesService;
import org.lucoenergia.conluz.domain.admin.community.energy.SupplyEnergyContribution;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisHourlyRecordsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves the community's supplies and period through the same scope resolver as the community energy
 * metrics, reads every supply's hourly records, and folds them hour by hour.
 *
 * <p>A supply has data in an hour when its record of that hour carries self-consumption, zero
 * included: the publication threshold over a single hour. Hours are matched across supplies by the
 * instant they start at, never by their local label, which two hours share on the day the clocks go
 * back.
 *
 * <h2>Query cost</h2>
 *
 * <p>One relational query (the supplies), then one InfluxDB read of the hourly records per supply, at
 * most 31 days of them.
 */
@Service
@Transactional(readOnly = true)
public class GetCommunityHourlyEnergySeriesServiceImpl implements GetCommunityHourlyEnergySeriesService {

    private final CommunityEnergyScopeResolver communityEnergyScopeResolver;
    private final GetDatadisHourlyRecordsRepository getDatadisHourlyRecordsRepository;

    public GetCommunityHourlyEnergySeriesServiceImpl(
            CommunityEnergyScopeResolver communityEnergyScopeResolver,
            GetDatadisHourlyRecordsRepository getDatadisHourlyRecordsRepository) {
        this.communityEnergyScopeResolver = communityEnergyScopeResolver;
        this.getDatadisHourlyRecordsRepository = getDatadisHourlyRecordsRepository;
    }

    @Override
    public List<CommunityEnergySeriesBucket> getHourlySeries(UUID communityId, OffsetDateTime startDate,
                                                             OffsetDateTime endDate) {

        CommunityEnergyScope scope = communityEnergyScopeResolver.resolve(communityId, startDate, endDate, null,
                EnergyMetricsPeriodValidator.MAX_HOURLY_LENGTH);

        // Explicit dates always resolve a period, its end clipped to the current hour. A scope without
        // one is a broken invariant, not an empty series.
        return scope.getPeriod()
                .map(period -> getHourlySeries(scope.getSupplies(), scope.getZone(), period))
                .orElseThrow(() -> new IllegalStateException(
                        "The community hourly energy series resolved a scope from explicit bounds without a "
                        + "period; explicit bounds always resolve one"));
    }

    private List<CommunityEnergySeriesBucket> getHourlySeries(List<Supply> supplies, ZoneId zone,
                                                              EnergyMetricsPeriod period) {
        Map<Instant, List<SupplyEnergyContribution>> contributionsByHour = new HashMap<>();
        for (Supply supply : supplies) {
            getDatadisHourlyRecordsRepository.forEachRecord(supply, period.getStartDate(), period.getEndDate(),
                    record -> {
                        if (record.getSelfConsumptionKWh() == null) {
                            return;
                        }
                        double surplusKWh = record.getSurplusKWh() == null ? 0d : record.getSurplusKWh();
                        contributionsByHour
                                .computeIfAbsent(record.getTime(), hour -> new ArrayList<>())
                                .add(new SupplyEnergyContribution(record.getSelfConsumptionKWh(), surplusKWh));
                    });
        }

        return period.hoursStartingIn(zone).stream()
                .map(hour -> CommunityEnergySeriesBucket.of(hour.atZone(zone).toOffsetDateTime(), supplies.size(),
                        contributionsByHour.getOrDefault(hour, List.of())))
                .toList();
    }
}
