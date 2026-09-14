package org.lucoenergia.conluz.infrastructure.consumption.datadis.metrics;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DatadisConsumptionAggregate;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetSupplyEnergyMetricsService;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.InvalidEnergyMetricsPeriodException;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.RecordedConsumptionPeriod;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.SupplyEnergyMetrics;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class GetSupplyEnergyMetricsServiceImpl implements GetSupplyEnergyMetricsService {

    private final GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final DateConverter dateConverter;

    public GetSupplyEnergyMetricsServiceImpl(
            GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository,
            GetSupplyRepository getSupplyRepository,
            DateConverter dateConverter) {
        this.getDatadisConsumptionAggregateRepository = getDatadisConsumptionAggregateRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.dateConverter = dateConverter;
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
                aggregate.getSurplusEnergyKWh());
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
