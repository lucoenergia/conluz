package org.lucoenergia.conluz.infrastructure.consumption.savings;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.tariff.DateRange;
import org.lucoenergia.conluz.domain.admin.supply.tariff.FlatPlan;
import org.lucoenergia.conluz.domain.admin.supply.tariff.SupplyTariffResolver;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSchedule;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSegment;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;
import org.lucoenergia.conluz.domain.admin.supply.tariff.UnsupportedTariffPlanException;
import org.lucoenergia.conluz.domain.consumption.SupplySavings;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.consumption.savings.SupplySavingsCalculator;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * A {@code @Component} rather than a {@code @Service} by design: it opens no transaction and
 * touches no JPA entity -- its only store is InfluxDB, through
 * {@link GetDatadisConsumptionAggregateRepository}, which manages its own connection per query.
 * Annotating it {@code @Service} would make {@code ServiceTransactionalArchTest} demand a
 * {@code @Transactional} that governs nothing, so the rule is sidestepped by not claiming a
 * stereotype whose contract this class does not have. Callers that do own a transaction (e.g.
 * {@code GetSupplyEnergyMetricsServiceImpl}, which is {@code @Transactional(readOnly = true)})
 * keep theirs; this class neither needs nor suspends it.
 */
@Component
public class SupplySavingsCalculatorImpl implements SupplySavingsCalculator {

    private final GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository;
    private final SupplyTariffResolver supplyTariffResolver;
    private final ZoneResolver zoneResolver;

    public SupplySavingsCalculatorImpl(
            GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository,
            @Qualifier("estimatedSupplyTariffResolver") SupplyTariffResolver supplyTariffResolver,
            ZoneResolver zoneResolver) {
        this.getDatadisConsumptionAggregateRepository = getDatadisConsumptionAggregateRepository;
        this.supplyTariffResolver = supplyTariffResolver;
        this.zoneResolver = zoneResolver;
    }

    @Override
    public SupplySavings estimate(Supply supply, Instant from, Instant toExclusive) {
        return price(supply, from, toExclusive, null);
    }

    @Override
    public SupplySavings estimate(Supply supply, Instant from, Instant toExclusive,
                                  double totalSelfConsumptionKWh) {
        return price(supply, from, toExclusive, totalSelfConsumptionKWh);
    }

    /**
     * Prices the interval segment by segment.
     *
     * <p>Precondition, guaranteed by {@link SupplyTariffResolver#scheduleFor}: the returned
     * schedule covers the requested range in its entirety, with no gap. Nothing here checks for
     * holes, because a schedule that had one could not have been constructed. The schedule may
     * however start before and end after the interval, so each segment is clamped to the
     * interval's real instants before being queried.
     *
     * @param knownTotalSelfConsumptionKWh the caller's own total for exactly this interval, or
     *                                     {@code null} when the caller has no such figure
     */
    private SupplySavings price(Supply supply, Instant from, Instant toExclusive,
                                Double knownTotalSelfConsumptionKWh) {

        SupplyId supplyId = SupplyId.of(supply.getId());
        ZoneId zone = zoneResolver.resolveZoneIdForSupply(supply.getId());
        TariffSchedule schedule = supplyTariffResolver.scheduleFor(supplyId,
                civilRangeCovering(from, toExclusive, zone));

        boolean singleSegment = schedule.getSegments().size() == 1;

        BigDecimal amount = BigDecimal.ZERO;
        for (TariffSegment segment : schedule.getSegments()) {
            double kWh = singleSegment && knownTotalSelfConsumptionKWh != null
                    ? knownTotalSelfConsumptionKWh
                    : selfConsumptionOf(segment, supply, from, toExclusive, zone);
            amount = amount.add(pricePerKwhIncludingVat(segment).multiply(BigDecimal.valueOf(kWh)));
        }

        return SupplySavings.of(amount, aggregateSourceOf(schedule));
    }

    /**
     * The civil date range containing the whole interval, which is what a resolver is asked for.
     *
     * <p>Tariff dates are civil dates in the resolved zone and a {@link DateRange} is half-open,
     * so the range's end is the civil date containing the interval's <em>last included</em>
     * instant, plus one day: an interval ending at {@code 2026-01-01T00:00} covers through
     * 2025-12-31, and must be asked for as {@code [.., 2026-01-01)}, never
     * {@code [.., 2025-12-32)}. Stepping back one nanosecond from the exclusive end is what names
     * that last included instant, and it is lossless because no series in this system is
     * sub-second -- the same reason {@code DateConverter.toExclusiveUpperBound} may nudge forward
     * by one. Taking the civil date of {@code toExclusive} directly would instead lose the tail of
     * any interval whose end is not a civil midnight.
     */
    private DateRange civilRangeCovering(Instant from, Instant toExclusive, ZoneId zone) {
        LocalDate start = from.atZone(zone).toLocalDate();
        LocalDate endExclusive = toExclusive.minusNanos(1).atZone(zone).toLocalDate().plusDays(1);
        return new DateRange(start, endExclusive);
    }

    /**
     * The self-consumption of the part of {@code segment} that falls inside the interval.
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
}
