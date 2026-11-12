package org.lucoenergia.conluz.infrastructure.datadis.get;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.SupplyConsumptionBucket;
import org.lucoenergia.conluz.domain.consumption.SupplySavings;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisEnergyValues;
import org.lucoenergia.conluz.domain.consumption.datadis.get.PublishedMonthlyConsumptionReader;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DailyPublishedAggregate;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.MonthlyPublishedAggregate;
import org.lucoenergia.conluz.domain.consumption.savings.SupplySavingsCalculator;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads every supply's hourly records totalled per local day in a single read, folds the days into
 * local calendar months, and keeps the months in which the supply's self-consumption is
 * {@link MonthlyPublishedAggregate#isPublishedWithin published}: the criterion the community monthly
 * series applies. The monthly pre-aggregates are not read, since they keep no count of the records
 * carrying self-consumption and store a month's missing self-consumption as 0.
 *
 * <p>A {@code @Component} rather than a {@code @Service}, as {@code SupplySavingsCalculatorImpl} is:
 * it opens no transaction and touches no JPA entity, and runs inside the caller's.
 *
 * <h2>Query cost</h2>
 *
 * <p>A single InfluxDB query for every supply, grouped by day and supply inside the store: at most
 * 731 rows per supply for the longest period. Each kept month is then priced, which issues further
 * queries only for a month straddling a tariff change.
 */
@Component
public class PublishedMonthlyConsumptionReaderImpl implements PublishedMonthlyConsumptionReader {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern(DateConverter.DATE_FORMAT);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern(DateConverter.TIME_FORMAT);

    private final GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository;
    private final SupplySavingsCalculator supplySavingsCalculator;

    public PublishedMonthlyConsumptionReaderImpl(
            GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository,
            SupplySavingsCalculator supplySavingsCalculator) {
        this.getDatadisConsumptionAggregateRepository = getDatadisConsumptionAggregateRepository;
        this.supplySavingsCalculator = supplySavingsCalculator;
    }

    @Override
    public Map<UUID, List<SupplyConsumptionBucket>> getPublishedMonths(List<Supply> supplies,
                                                                       EnergyMetricsPeriod period, ZoneId zone) {
        Instant from = period.getStartDate().toInstant();
        Instant toExclusive = period.getEndDate().toInstant();
        List<YearMonth> months = period.monthsStartingIn(zone);

        Map<String, List<DailyPublishedAggregate>> daysBySupplyCode =
                getDatadisConsumptionAggregateRepository.aggregateByLocalDay(supplies, from, toExclusive, zone);

        Map<UUID, List<SupplyConsumptionBucket>> bucketsBySupply = new HashMap<>();
        for (Supply supply : supplies) {
            Map<YearMonth, MonthlyPublishedAggregate> foldedMonths = MonthlyPublishedAggregate.foldIntoMonths(
                    daysBySupplyCode.getOrDefault(supply.getCode(), List.of()));
            List<SupplyConsumptionBucket> buckets = new ArrayList<>();
            // The selected months, in order: a day before the first of them belongs to no bucket.
            for (YearMonth month : months) {
                MonthlyPublishedAggregate totals = foldedMonths.get(month);
                if (totals != null && totals.isPublishedWithin(period, zone)) {
                    buckets.add(price(supply, totals, period, zone));
                }
            }
            if (!buckets.isEmpty()) {
                bucketsBySupply.put(supply.getId(), buckets);
            }
        }
        return bucketsBySupply;
    }

    /**
     * Prices the month over its hours inside the period -- the same interval its energy totals --
     * handing the calculator the month's self-consumption as the bucket reports it, as the daily
     * series does for a day.
     * A selected month starts inside the period, so only its end can need clipping.
     */
    private SupplyConsumptionBucket price(Supply supply, MonthlyPublishedAggregate totals,
                                          EnergyMetricsPeriod period, ZoneId zone) {
        EnergyMetricsPeriod month = EnergyMetricsPeriod.ofMonth(totals.month(), zone);
        Instant from = month.getStartDate().toInstant();
        Instant monthEnd = month.getEndDate().toInstant();
        Instant periodEnd = period.getEndDate().toInstant();
        Instant toExclusive = monthEnd.isBefore(periodEnd) ? monthEnd : periodEnd;

        DatadisConsumption consumption = toConsumption(supply, totals);
        // The figure reported beside the savings, read exactly as reported, as the daily series prices.
        SupplySavings savings = supplySavingsCalculator.estimate(supply, from, toExclusive,
                DatadisEnergyValues.exactlyAsReported(consumption.getSelfConsumptionEnergyKWh()).doubleValue());
        return SupplyConsumptionBucket.of(consumption, savings, true);
    }

    /**
     * The month's totals in the shape the per-supply series reports, each figure narrowed to a float
     * exactly as the pre-aggregated monthly figure it replaces was read.
     */
    private static DatadisConsumption toConsumption(Supply supply, MonthlyPublishedAggregate totals) {
        DatadisConsumption consumption = new DatadisConsumption();
        consumption.setCups(supply.getCode());
        consumption.setDate(DATE.format(totals.month().atDay(1)));
        consumption.setTime(TIME.format(LocalTime.MIDNIGHT));
        consumption.setConsumptionKWh((float) totals.consumptionKWh());
        consumption.setSelfConsumptionEnergyKWh((float) totals.selfConsumptionKWh());
        consumption.setSurplusEnergyKWh((float) totals.surplusKWh());
        consumption.setObtainMethod(totals.obtainMethod());
        return consumption;
    }
}
