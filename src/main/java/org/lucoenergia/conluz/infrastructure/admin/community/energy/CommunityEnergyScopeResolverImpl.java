package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyScope;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyScopeResolver;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsReferencePeriod;
import org.lucoenergia.conluz.domain.consumption.RecordedConsumptionPeriod;
import org.lucoenergia.conluz.domain.consumption.ReferenceMonthResolver;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Validates the requested period before anything is read, then lists the community's enabled
 * supplies and resolves the period for them, as
 * {@link org.lucoenergia.conluz.infrastructure.admin.community.membership.energymetrics.MembershipEnergyMetricsScopeResolverImpl}
 * does for a membership.
 *
 * <h2>Query cost</h2>
 *
 * <p>One relational query (the supplies). Resolving the period adds, for the reference month, what
 * {@link org.lucoenergia.conluz.infrastructure.consumption.ReferenceMonthResolverImpl} costs -- two
 * queries per supply and usually a single count -- or two per supply for the recorded range; an
 * explicit period adds none.
 */
@Component
public class CommunityEnergyScopeResolverImpl implements CommunityEnergyScopeResolver {

    private final GetSupplyRepository getSupplyRepository;
    private final GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository;
    private final ReferenceMonthResolver referenceMonthResolver;
    private final ZoneResolver zoneResolver;
    private final DateConverter dateConverter;
    private final Clock clock;

    public CommunityEnergyScopeResolverImpl(
            GetSupplyRepository getSupplyRepository,
            GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository,
            ReferenceMonthResolver referenceMonthResolver,
            ZoneResolver zoneResolver,
            DateConverter dateConverter,
            Clock clock) {
        this.getSupplyRepository = getSupplyRepository;
        this.getDatadisConsumptionAggregateRepository = getDatadisConsumptionAggregateRepository;
        this.referenceMonthResolver = referenceMonthResolver;
        this.zoneResolver = zoneResolver;
        this.dateConverter = dateConverter;
        this.clock = clock;
    }

    @Override
    public CommunityEnergyScope resolve(UUID communityId, OffsetDateTime startDate, OffsetDateTime endDate,
                                        EnergyMetricsReferencePeriod referencePeriod, Period maxLength) {

        ZoneId zone = zoneResolver.resolveZoneIdForCommunity(communityId);
        OffsetDateTime startOfCurrentHour = EnergyMetricsPeriod.startOfCurrentHour(clock, zone);
        EnergyMetricsPeriodValidator.validate(startDate, endDate, referencePeriod, startOfCurrentHour, zone,
                maxLength);

        // Whatever the membership status of the owner: the energy flowed all the same.
        List<Supply> supplies = getSupplyRepository.findAllByCommunityId(communityId).stream()
                .filter(Supply::getEnabled)
                .toList();

        Optional<EnergyMetricsPeriod> period = resolvePeriod(zone, startOfCurrentHour, supplies, startDate,
                endDate, referencePeriod);

        return new CommunityEnergyScope(supplies, zone, period.orElse(null));
    }

    /**
     * The period to compute over, or empty when none can be resolved. An explicit period always
     * resolves, even for a community without supplies, with its end clipped to the current hour.
     */
    private Optional<EnergyMetricsPeriod> resolvePeriod(ZoneId zone, OffsetDateTime startOfCurrentHour,
                                                        List<Supply> supplies,
                                                        OffsetDateTime startDate, OffsetDateTime endDate,
                                                        EnergyMetricsReferencePeriod referencePeriod) {
        if (startDate != null) {
            return Optional.of(new EnergyMetricsPeriod(startDate, endDate).endingNoLaterThan(startOfCurrentHour));
        }
        if (referencePeriod == EnergyMetricsReferencePeriod.LATEST_PUBLISHED_MONTH) {
            return referenceMonthResolver.resolveLatestPublishedMonth(supplies, zone)
                    .map(month -> EnergyMetricsPeriod.ofMonth(month, zone));
        }
        return recordedPeriod(supplies);
    }

    /**
     * From the earliest record stored for any of the supplies to the end of the hour of the latest,
     * or empty when none of them has stored a record.
     */
    private Optional<EnergyMetricsPeriod> recordedPeriod(List<Supply> supplies) {
        List<RecordedConsumptionPeriod> recorded = supplies.stream()
                .map(getDatadisConsumptionAggregateRepository::findRecordedPeriod)
                .flatMap(Optional::stream)
                .toList();
        if (recorded.isEmpty()) {
            return Optional.empty();
        }
        Instant firstRecord = recorded.stream().map(RecordedConsumptionPeriod::getFirstRecord)
                .min(Instant::compareTo).orElseThrow();
        Instant lastRecord = recorded.stream().map(RecordedConsumptionPeriod::getLastRecord)
                .max(Instant::compareTo).orElseThrow();
        return Optional.of(EnergyMetricsPeriod.ofRecords(
                dateConverter.convertInstantToOffsetDateTime(firstRecord),
                dateConverter.convertInstantToOffsetDateTime(lastRecord)));
    }
}
