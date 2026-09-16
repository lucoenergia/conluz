package org.lucoenergia.conluz.infrastructure.consumption;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.tariff.DateRange;
import org.lucoenergia.conluz.domain.admin.supply.tariff.FlatPlan;
import org.lucoenergia.conluz.domain.admin.supply.tariff.SupplyTariffResolver;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSchedule;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSegment;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;
import org.lucoenergia.conluz.domain.admin.supply.tariff.UnsupportedTariffPlanException;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DatadisConsumptionAggregate;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.consumption.GetSupplyEnergyMetricsService;
import org.lucoenergia.conluz.domain.consumption.InvalidEnergyMetricsPeriodException;
import org.lucoenergia.conluz.domain.consumption.RecordedConsumptionPeriod;
import org.lucoenergia.conluz.domain.consumption.SupplyEnergyMetrics;
import org.lucoenergia.conluz.domain.consumption.SupplySavings;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class GetSupplyEnergyMetricsServiceImpl implements GetSupplyEnergyMetricsService {

    private final GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final DateConverter dateConverter;
    private final SupplyTariffResolver supplyTariffResolver;
    private final ZoneResolver zoneResolver;

    public GetSupplyEnergyMetricsServiceImpl(
            GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository,
            GetSupplyRepository getSupplyRepository,
            DateConverter dateConverter,
            @Qualifier("estimatedSupplyTariffResolver") SupplyTariffResolver supplyTariffResolver,
            ZoneResolver zoneResolver) {
        this.getDatadisConsumptionAggregateRepository = getDatadisConsumptionAggregateRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.dateConverter = dateConverter;
        this.supplyTariffResolver = supplyTariffResolver;
        this.zoneResolver = zoneResolver;
    }

    @Override
    public SupplyEnergyMetrics getEnergyMetrics(SupplyId supplyId, OffsetDateTime startDate, OffsetDateTime endDate) {

        validatePeriod(startDate, endDate);

        Supply supply = getSupplyOrThrow(supplyId);

        OffsetDateTime resolvedStartDate = startDate;
        OffsetDateTime resolvedEndDate = endDate;

        if (resolvedStartDate == null) {
            Optional<RecordedConsumptionPeriod> recordedPeriod =
                    getDatadisConsumptionAggregateRepository.findRecordedPeriod(supply);
            if (recordedPeriod.isEmpty()) {
                // The supply has never stored a consumption record, so there is no period to
                // report. This is an empty result, not a missing supply.
                return SupplyEnergyMetrics.empty(supply, null, null, 0L);
            }
            resolvedStartDate = dateConverter.convertInstantToOffsetDateTime(recordedPeriod.get().getFirstRecord());
            resolvedEndDate = dateConverter.convertInstantToOffsetDateTime(recordedPeriod.get().getLastRecord());
        }

        DatadisConsumptionAggregate aggregate = getDatadisConsumptionAggregateRepository
                .aggregateByRangeOfDates(supply, resolvedStartDate, resolvedEndDate);

        return new SupplyEnergyMetrics(
                supply,
                resolvedStartDate,
                resolvedEndDate,
                aggregate.getHoursWithData(),
                expectedHours(resolvedStartDate, resolvedEndDate),
                aggregate.getConsumptionKWh(),
                aggregate.getSelfConsumptionEnergyKWh(),
                aggregate.getSurplusEnergyKWh(),
                estimateSavings(supplyId, supply, resolvedStartDate, resolvedEndDate,
                        aggregate.getSelfConsumptionEnergyKWh()));
    }

    /**
     * Prices the self-consumed energy of the resolved period, segment by segment.
     *
     * <p>The period's public end is inclusive; it is converted to an exclusive instant
     * <strong>here and only here</strong>, and every downstream use -- the civil range the
     * resolver is asked for, the clamping of each segment, the half-open sums -- consumes that
     * one instant pair, so the priced instants and the aggregated instants are the same set by
     * construction.
     *
     * <p>Precondition, guaranteed by {@link SupplyTariffResolver#scheduleFor}: the returned
     * schedule covers the requested range in its entirety, with no gap. Nothing here checks for
     * holes, because a schedule that had one could not have been constructed.
     */
    private SupplySavings estimateSavings(SupplyId supplyId, Supply supply, OffsetDateTime startDate,
                                          OffsetDateTime endDate, double totalSelfConsumptionKWh) {

        Instant from = startDate.toInstant();
        Instant toExclusive = DateConverter.toExclusiveUpperBound(endDate);

        ZoneId zone = zoneResolver.resolveZoneIdForSupply(supplyId.getId());
        TariffSchedule schedule = supplyTariffResolver.scheduleFor(supplyId, civilRangeCovering(startDate, endDate, zone));

        boolean singleSegment = schedule.getSegments().size() == 1;

        BigDecimal amount = BigDecimal.ZERO;
        for (TariffSegment segment : schedule.getSegments()) {
            // A lone segment necessarily covers the whole period, so its clamped interval is
            // exactly the one the aggregate already summed. Reusing that total keeps the amount
            // consistent with the reported selfConsumptionKWh to the last bit, and costs no query.
            double kWh = singleSegment
                    ? totalSelfConsumptionKWh
                    : selfConsumptionOf(segment, supply, from, toExclusive, zone);
            amount = amount.add(pricePerKwhIncludingVat(segment).multiply(BigDecimal.valueOf(kWh)));
        }

        return SupplySavings.of(amount, aggregateSourceOf(schedule));
    }

    /**
     * The civil date range containing the whole period, which is what a resolver is asked for.
     *
     * <p>Tariff dates are civil dates in the configured zone and a {@link DateRange} is half-open,
     * so an inclusive end maps to the civil date containing it <em>plus one day</em>: a period
     * ending on 2025-12-31 is {@code [.., 2026-01-01)}, never {@code [.., 2025-12-31)}, which
     * would leave its last day unpriced. The range is derived from the public inclusive end
     * rather than from the exclusive instant, so the day it names is the day the caller asked
     * for.
     */
    private DateRange civilRangeCovering(OffsetDateTime startDate, OffsetDateTime endDate, ZoneId zone) {
        return new DateRange(
                startDate.atZoneSameInstant(zone).toLocalDate(),
                endDate.atZoneSameInstant(zone).toLocalDate().plusDays(1));
    }

    /**
     * The self-consumption of the part of {@code segment} that falls inside the period. The
     * schedule may start before and end after the period it was resolved for, so each segment is
     * clamped to the period's real instants before being queried.
     */
    private double selfConsumptionOf(TariffSegment segment, Supply supply, Instant from, Instant toExclusive,
                                     ZoneId zone) {
        Instant segmentFrom = max(segment.getRange().getStart().atStartOfDay(zone).toInstant(), from);
        Instant segmentTo = min(segment.getRange().getEnd().atStartOfDay(zone).toInstant(), toExclusive);

        if (!segmentFrom.isBefore(segmentTo)) {
            return 0d;
        }
        return getDatadisConsumptionAggregateRepository.sumSelfConsumptionKWh(supply, segmentFrom, segmentTo);
    }

    /**
     * The price a kWh consumed under this segment saved, taxes included. The plan's price is the
     * taxable base and VAT travels with the segment, so the two are multiplied here rather than
     * being folded into the plan.
     */
    private BigDecimal pricePerKwhIncludingVat(TariffSegment segment) {
        if (!(segment.getPlan() instanceof FlatPlan flatPlan)) {
            throw new UnsupportedTariffPlanException(segment.getPlan().getClass());
        }
        return flatPlan.getPricePerKwh().multiply(BigDecimal.ONE.add(segment.getVatRate()));
    }

    /**
     * One source for a figure derived from several segments. A total is only as trustworthy as
     * its least trustworthy part, so a single estimated segment makes the whole amount an
     * estimate; {@code REAL_TARIFF} requires every segment to be contracted.
     */
    private TariffSource aggregateSourceOf(TariffSchedule schedule) {
        boolean anyEstimated = schedule.getSegments().stream()
                .anyMatch(segment -> segment.getSource() == TariffSource.ESTIMATE);
        return anyEstimated ? TariffSource.ESTIMATE : TariffSource.REAL_TARIFF;
    }

    private static Instant max(Instant left, Instant right) {
        return left.isAfter(right) ? left : right;
    }

    private static Instant min(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    private void validatePeriod(OffsetDateTime startDate, OffsetDateTime endDate) {
        if ((startDate == null) != (endDate == null)) {
            throw new InvalidEnergyMetricsPeriodException(
                    InvalidEnergyMetricsPeriodException.Reason.INCOMPLETE_PERIOD);
        }
        if (startDate != null && startDate.isAfter(endDate)) {
            throw new InvalidEnergyMetricsPeriodException(
                    InvalidEnergyMetricsPeriodException.Reason.START_AFTER_END);
        }
    }

    /**
     * The number of hourly slots the period spans, both bounds inclusive. Counting the elapsed
     * time between two instants that carry their offset is what makes a day with a daylight
     * saving transition come out as 23 or 25 hours instead of 24.
     */
    private long expectedHours(OffsetDateTime startDate, OffsetDateTime endDate) {
        return ChronoUnit.HOURS.between(startDate, endDate) + 1;
    }

    private Supply getSupplyOrThrow(SupplyId supplyId) {
        return getSupplyRepository.findById(supplyId)
                .orElseThrow(() -> new SupplyNotFoundException(supplyId));
    }
}
