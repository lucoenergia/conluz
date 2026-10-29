package org.lucoenergia.conluz.infrastructure.consumption;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DatadisConsumptionAggregate;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.consumption.GetSupplyEnergyMetricsService;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsReferencePeriod;
import org.lucoenergia.conluz.domain.consumption.RecordedConsumptionPeriod;
import org.lucoenergia.conluz.domain.consumption.ReferenceMonthResolver;
import org.lucoenergia.conluz.domain.consumption.SupplyEnergyMetrics;
import org.lucoenergia.conluz.domain.consumption.SupplySavings;
import org.lucoenergia.conluz.domain.consumption.savings.SupplySavingsCalculator;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class GetSupplyEnergyMetricsServiceImpl implements GetSupplyEnergyMetricsService {

    private final GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final GetPlantRepository getPlantRepository;
    private final DateConverter dateConverter;
    private final SupplySavingsCalculator supplySavingsCalculator;
    private final ReferenceMonthResolver referenceMonthResolver;
    private final ZoneResolver zoneResolver;
    private final Clock clock;

    public GetSupplyEnergyMetricsServiceImpl(
            GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository,
            GetSupplyRepository getSupplyRepository,
            GetPlantRepository getPlantRepository,
            DateConverter dateConverter,
            SupplySavingsCalculator supplySavingsCalculator,
            ReferenceMonthResolver referenceMonthResolver,
            ZoneResolver zoneResolver,
            Clock clock) {
        this.getDatadisConsumptionAggregateRepository = getDatadisConsumptionAggregateRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.getPlantRepository = getPlantRepository;
        this.dateConverter = dateConverter;
        this.supplySavingsCalculator = supplySavingsCalculator;
        this.referenceMonthResolver = referenceMonthResolver;
        this.zoneResolver = zoneResolver;
        this.clock = clock;
    }

    @Override
    public SupplyEnergyMetrics getEnergyMetrics(SupplyId supplyId, OffsetDateTime startDate, OffsetDateTime endDate) {
        return getEnergyMetrics(supplyId, startDate, endDate, null);
    }

    @Override
    public SupplyEnergyMetrics getEnergyMetrics(SupplyId supplyId, OffsetDateTime startDate, OffsetDateTime endDate,
                                                EnergyMetricsReferencePeriod referencePeriod) {

        ZoneId zone = zoneResolver.resolveZoneIdForSupply(supplyId.getId());
        OffsetDateTime startOfCurrentHour = EnergyMetricsPeriod.startOfCurrentHour(clock, zone);
        EnergyMetricsPeriodValidator.validate(startDate, endDate, referencePeriod, startOfCurrentHour, zone);

        Supply supply = getSupplyOrThrow(supplyId);
        // Read once, before any branch, so every resolution, the empty ones included, carries it (SEM-003).
        boolean plantSupply = getPlantRepository.existsBySupply(supplyId);

        OffsetDateTime resolvedStartDate = startDate;
        // An explicit end after the current hour is clipped to it before anything is read, so no
        // hour that has not happened is summed or expected, and the response reports the end used.
        OffsetDateTime resolvedEndDate = startDate == null ? null
                : new EnergyMetricsPeriod(startDate, endDate).endingNoLaterThan(startOfCurrentHour).getEndDate();

        if (referencePeriod == EnergyMetricsReferencePeriod.LATEST_PUBLISHED_MONTH) {
            Optional<EnergyMetricsPeriod> latestPublishedMonth = latestPublishedMonth(supply, zone);
            if (latestPublishedMonth.isEmpty()) {
                // No month in the search window is published for this supply. The same empty
                // result as a supply without any record, not an error.
                return SupplyEnergyMetrics.empty(supply, null, null, 0L, plantSupply);
            }
            resolvedStartDate = latestPublishedMonth.get().getStartDate();
            resolvedEndDate = latestPublishedMonth.get().getEndDate();
        } else if (resolvedStartDate == null) {
            Optional<RecordedConsumptionPeriod> recordedPeriod =
                    getDatadisConsumptionAggregateRepository.findRecordedPeriod(supply);
            if (recordedPeriod.isEmpty()) {
                // The supply has never stored a consumption record, so there is no period to
                // report. This is an empty result, not a missing supply.
                return SupplyEnergyMetrics.empty(supply, null, null, 0L, plantSupply);
            }
            EnergyMetricsPeriod recorded = EnergyMetricsPeriod.ofRecords(
                    dateConverter.convertInstantToOffsetDateTime(recordedPeriod.get().getFirstRecord()),
                    dateConverter.convertInstantToOffsetDateTime(recordedPeriod.get().getLastRecord()));
            resolvedStartDate = recorded.getStartDate();
            resolvedEndDate = recorded.getEndDate();
        }

        DatadisConsumptionAggregate aggregate = getDatadisConsumptionAggregateRepository
                .aggregateByRangeOfDates(supply, resolvedStartDate, resolvedEndDate);

        return new SupplyEnergyMetrics(
                supply,
                resolvedStartDate,
                resolvedEndDate,
                aggregate.getHoursWithData(),
                new EnergyMetricsPeriod(resolvedStartDate, resolvedEndDate).getExpectedHours(),
                aggregate.getConsumptionKWh(),
                aggregate.getSelfConsumptionEnergyKWh(),
                aggregate.getSurplusEnergyKWh(),
                estimateSavings(supply, resolvedStartDate, resolvedEndDate,
                        aggregate.getSelfConsumptionEnergyKWh()),
                plantSupply);
    }

    /**
     * Prices the self-consumed energy of the resolved period.
     *
     * <p>The period is half-open, as the aggregate's, and the calculator consumes that one instant
     * pair for the civil range it resolves a tariff for, for the clamping of each segment and for
     * its half-open sums -- so the priced instants and the instants the aggregate already summed
     * are the same set by construction.
     *
     * <p>The aggregate's own total is handed over so a period covered by a single tariff segment
     * reports an amount consistent with the {@code selfConsumptionKWh} beside it, without a
     * second query for energy already counted.
     */
    private SupplySavings estimateSavings(Supply supply, OffsetDateTime startDate, OffsetDateTime endDate,
                                          double totalSelfConsumptionKWh) {
        return supplySavingsCalculator.estimate(
                supply,
                startDate.toInstant(),
                endDate.toInstant(),
                totalSelfConsumptionKWh);
    }

    /**
     * The latest month published for this supply alone: its owner's other supplies never lend it
     * a month it has not published itself.
     */
    private Optional<EnergyMetricsPeriod> latestPublishedMonth(Supply supply, ZoneId zone) {
        return referenceMonthResolver.resolveLatestPublishedMonth(List.of(supply), zone)
                .map(month -> EnergyMetricsPeriod.ofMonth(month, zone));
    }

    private Supply getSupplyOrThrow(SupplyId supplyId) {
        return getSupplyRepository.findById(supplyId)
                .orElseThrow(() -> new SupplyNotFoundException(supplyId));
    }
}
