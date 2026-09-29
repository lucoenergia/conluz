package org.lucoenergia.conluz.infrastructure.admin.community.membership.energymetrics;

import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.GetMembershipEnergyMetricsService;
import org.lucoenergia.conluz.domain.admin.community.membership.energymetrics.MembershipEnergyMetrics;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsReferencePeriod;
import org.lucoenergia.conluz.domain.consumption.GetSupplyEnergyMetricsService;
import org.lucoenergia.conluz.domain.consumption.RecordedConsumptionPeriod;
import org.lucoenergia.conluz.domain.consumption.ReferenceMonthResolver;
import org.lucoenergia.conluz.domain.consumption.SupplyEnergyMetrics;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
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
 * <p>Two relational queries (membership, supplies), then per supply: one supply lookup, one
 * aggregate query and one pricing query per tariff segment. Resolving the period adds one query per
 * supply for the reference month, or two per supply for the recorded range; an explicit period adds
 * none. The cost grows with the number of supplies, not with the length of the period, since every
 * sum is pushed down to the store.
 */
@Service
@Transactional(readOnly = true)
public class GetMembershipEnergyMetricsServiceImpl implements GetMembershipEnergyMetricsService {

    /**
     * The hour of the last hourly record of a day. Both period bounds are inclusive, so a period
     * ending on a day ends on this hour rather than on the following midnight.
     */
    private static final LocalTime LAST_HOUR_OF_DAY = LocalTime.of(23, 0);

    private final GetMembershipsRepository getMembershipsRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final GetSupplyEnergyMetricsService getSupplyEnergyMetricsService;
    private final GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository;
    private final ReferenceMonthResolver referenceMonthResolver;
    private final ZoneResolver zoneResolver;
    private final DateConverter dateConverter;

    public GetMembershipEnergyMetricsServiceImpl(
            GetMembershipsRepository getMembershipsRepository,
            GetSupplyRepository getSupplyRepository,
            GetSupplyEnergyMetricsService getSupplyEnergyMetricsService,
            GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository,
            ReferenceMonthResolver referenceMonthResolver,
            ZoneResolver zoneResolver,
            DateConverter dateConverter) {
        this.getMembershipsRepository = getMembershipsRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.getSupplyEnergyMetricsService = getSupplyEnergyMetricsService;
        this.getDatadisConsumptionAggregateRepository = getDatadisConsumptionAggregateRepository;
        this.referenceMonthResolver = referenceMonthResolver;
        this.zoneResolver = zoneResolver;
        this.dateConverter = dateConverter;
    }

    @Override
    public MembershipEnergyMetrics getEnergyMetrics(UUID communityId, UUID userId, OffsetDateTime startDate,
                                                    OffsetDateTime endDate,
                                                    EnergyMetricsReferencePeriod referencePeriod) {

        EnergyMetricsPeriodValidator.validate(startDate, endDate, referencePeriod);

        getMembershipsRepository.findByUserIdAndCommunityId(userId, communityId)
                .orElseThrow(() -> new MembershipNotFoundException(communityId, userId));

        List<Supply> supplies = getSupplyRepository.findAllByOwnerAndCommunityId(UserId.of(userId), communityId);

        Optional<Period> period = resolvePeriod(communityId, supplies, startDate, endDate, referencePeriod);
        if (period.isEmpty()) {
            return MembershipEnergyMetrics.withoutPeriod(supplies.size());
        }

        OffsetDateTime resolvedStartDate = period.get().startDate();
        OffsetDateTime resolvedEndDate = period.get().endDate();
        List<SupplyEnergyMetrics> metrics = supplies.stream()
                .map(supply -> getSupplyEnergyMetricsService.getEnergyMetrics(
                        SupplyId.of(supply.getId()), resolvedStartDate, resolvedEndDate))
                .toList();

        return MembershipEnergyMetrics.of(resolvedStartDate, resolvedEndDate, metrics);
    }

    /**
     * The period to compute over, or empty when none can be resolved. An explicit period always
     * resolves, even for a membership without supplies.
     */
    private Optional<Period> resolvePeriod(UUID communityId, List<Supply> supplies, OffsetDateTime startDate,
                                           OffsetDateTime endDate,
                                           EnergyMetricsReferencePeriod referencePeriod) {
        if (startDate != null) {
            return Optional.of(new Period(startDate, endDate));
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
    private static Period monthPeriod(YearMonth month, ZoneId zone) {
        return new Period(
                month.atDay(1).atStartOfDay(zone).toOffsetDateTime(),
                month.atEndOfMonth().atTime(LAST_HOUR_OF_DAY).atZone(zone).toOffsetDateTime());
    }

    /**
     * From the earliest record stored for any of the supplies to the latest, or empty when none of
     * them has stored a record.
     */
    private Optional<Period> recordedPeriod(List<Supply> supplies) {
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
        return Optional.of(new Period(
                dateConverter.convertInstantToOffsetDateTime(firstRecord),
                dateConverter.convertInstantToOffsetDateTime(lastRecord)));
    }

    private record Period(OffsetDateTime startDate, OffsetDateTime endDate) {
    }
}
