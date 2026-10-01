package org.lucoenergia.conluz.infrastructure.admin.community.membership.energymetrics;

import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetricsScope;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetricsScopeResolver;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsReferencePeriod;
import org.lucoenergia.conluz.domain.consumption.RecordedConsumptionPeriod;
import org.lucoenergia.conluz.domain.consumption.ReferenceMonthResolver;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Validates the requested period before anything is read, then looks up the membership and its
 * supplies and resolves the period for them.
 *
 * <h2>Query cost</h2>
 *
 * <p>Two relational queries (membership, supplies). Resolving the period adds one query per supply
 * for the reference month, or two per supply for the recorded range; an explicit period adds none.
 */
@Component
public class MembershipEnergyMetricsScopeResolverImpl implements MembershipEnergyMetricsScopeResolver {

    /**
     * The hour of the last hourly record of a day. Both period bounds are inclusive, so a period
     * ending on a day ends on this hour rather than on the following midnight.
     */
    private static final LocalTime LAST_HOUR_OF_DAY = LocalTime.of(23, 0);

    private final GetMembershipsRepository getMembershipsRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository;
    private final ReferenceMonthResolver referenceMonthResolver;
    private final ZoneResolver zoneResolver;
    private final DateConverter dateConverter;

    public MembershipEnergyMetricsScopeResolverImpl(
            GetMembershipsRepository getMembershipsRepository,
            GetSupplyRepository getSupplyRepository,
            GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository,
            ReferenceMonthResolver referenceMonthResolver,
            ZoneResolver zoneResolver,
            DateConverter dateConverter) {
        this.getMembershipsRepository = getMembershipsRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.getDatadisConsumptionAggregateRepository = getDatadisConsumptionAggregateRepository;
        this.referenceMonthResolver = referenceMonthResolver;
        this.zoneResolver = zoneResolver;
        this.dateConverter = dateConverter;
    }

    @Override
    public MembershipEnergyMetricsScope resolve(UUID communityId, UUID userId, OffsetDateTime startDate,
                                                OffsetDateTime endDate,
                                                EnergyMetricsReferencePeriod referencePeriod) {

        EnergyMetricsPeriodValidator.validate(startDate, endDate, referencePeriod);

        getMembershipsRepository.findByUserIdAndCommunityId(userId, communityId)
                .orElseThrow(() -> new MembershipNotFoundException(communityId, userId));

        List<Supply> supplies = getSupplyRepository.findAllByOwnerAndCommunityId(UserId.of(userId), communityId);

        Optional<EnergyMetricsPeriod> period = resolvePeriod(communityId, supplies, startDate, endDate,
                referencePeriod);

        return new MembershipEnergyMetricsScope(supplies, period.orElse(null));
    }

    @Override
    public MembershipEnergyMetricsScope resolveLatestPublishedMonth(UUID communityId, UUID userId) {
        return resolve(communityId, userId, null, null, EnergyMetricsReferencePeriod.LATEST_PUBLISHED_MONTH);
    }

    /**
     * The period to compute over, or empty when none can be resolved. An explicit period always
     * resolves, even for a membership without supplies.
     */
    private Optional<EnergyMetricsPeriod> resolvePeriod(UUID communityId, List<Supply> supplies,
                                                        OffsetDateTime startDate, OffsetDateTime endDate,
                                                        EnergyMetricsReferencePeriod referencePeriod) {
        if (startDate != null) {
            return Optional.of(new EnergyMetricsPeriod(startDate, endDate));
        }
        if (referencePeriod == EnergyMetricsReferencePeriod.LATEST_PUBLISHED_MONTH) {
            ZoneId zone = zoneResolver.resolveZoneIdForCommunity(communityId);
            return referenceMonthResolver.resolveLatestPublishedMonth(supplies, zone)
                    .map(month -> monthPeriod(month, zone));
        }
        return recordedPeriod(supplies);
    }

    /**
     * From the first hour of the month to its last, both inclusive, each carrying the offset in
     * force at that instant.
     */
    private static EnergyMetricsPeriod monthPeriod(YearMonth month, ZoneId zone) {
        return new EnergyMetricsPeriod(
                month.atDay(1).atStartOfDay(zone).toOffsetDateTime(),
                month.atEndOfMonth().atTime(LAST_HOUR_OF_DAY).atZone(zone).toOffsetDateTime());
    }

    /**
     * From the earliest record stored for any of the supplies to the latest, or empty when none of
     * them has stored a record.
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
        return Optional.of(new EnergyMetricsPeriod(
                dateConverter.convertInstantToOffsetDateTime(firstRecord),
                dateConverter.convertInstantToOffsetDateTime(lastRecord)));
    }
}
