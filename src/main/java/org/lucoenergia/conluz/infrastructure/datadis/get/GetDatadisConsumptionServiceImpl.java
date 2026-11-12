package org.lucoenergia.conluz.infrastructure.datadis.get;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.consumption.PublicationThreshold;
import org.lucoenergia.conluz.domain.consumption.SupplyConsumptionBucket;
import org.lucoenergia.conluz.domain.consumption.SupplyHourlyConsumptionBucket;
import org.lucoenergia.conluz.domain.consumption.SupplySavings;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisDailyConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisEnergyValues;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisHourlyConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionService;
import org.lucoenergia.conluz.domain.consumption.savings.SupplySavingsCalculator;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class GetDatadisConsumptionServiceImpl implements GetDatadisConsumptionService {

    private final GetDatadisConsumptionRepository getDatadisConsumptionRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final SupplySavingsCalculator supplySavingsCalculator;
    private final ZoneResolver zoneResolver;
    private final Clock clock;

    public GetDatadisConsumptionServiceImpl(
            @Qualifier("getDatadisConsumptionRepositoryInflux") GetDatadisConsumptionRepository getDatadisConsumptionRepository,
            GetSupplyRepository getSupplyRepository,
            SupplySavingsCalculator supplySavingsCalculator,
            ZoneResolver zoneResolver,
            Clock clock) {
        this.getDatadisConsumptionRepository = getDatadisConsumptionRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.supplySavingsCalculator = supplySavingsCalculator;
        this.zoneResolver = zoneResolver;
        this.clock = clock;
    }

    @Override
    public List<SupplyHourlyConsumptionBucket> getHourlySeriesBySupply(SupplyId supplyId, OffsetDateTime startDate,
                                                                       OffsetDateTime endDate) {
        ZoneId zone = zoneResolver.resolveZoneIdForSupply(supplyId.getId());
        OffsetDateTime effectiveEndDate = effectiveEndDate(startDate, endDate, zone,
                EnergyMetricsPeriodValidator.MAX_HOURLY_LENGTH);
        Supply supply = getSupplyOrThrow(supplyId);

        Map<Instant, DatadisHourlyConsumption> storedHours = new HashMap<>();
        for (DatadisHourlyConsumption hour : getDatadisConsumptionRepository.getHourlyConsumptionsByPeriod(
                supply, startDate, effectiveEndDate)) {
            storedHours.put(hour.getStartsAt(), hour);
        }

        Instant rangeToExclusive = effectiveEndDate.toInstant();
        return new EnergyMetricsPeriod(startDate, effectiveEndDate).hoursStartingIn(zone).stream()
                .map(hour -> {
                    DatadisHourlyConsumption stored = storedHours.get(hour);
                    DatadisConsumption consumption = stored != null
                            ? stored.getConsumption()
                            : nothingStored(supply, hour, zone);
                    boolean publishedData = stored != null && stored.getPublishedHours() > 0;
                    return priceHour(supply, consumption, publishedData, hour, zone, rangeToExclusive);
                })
                .toList();
    }

    @Override
    public List<DatadisConsumption> getYearlyConsumptionBySupply(SupplyId supplyId, OffsetDateTime startDate,
                                                                  OffsetDateTime endDate) {
        Supply supply = getSupplyOrThrow(supplyId);
        return getDatadisConsumptionRepository.getYearlyConsumptionsByRangeOfDates(supply, startDate, endDate);
    }

    @Override
    public List<SupplyConsumptionBucket> getDailySeriesBySupply(SupplyId supplyId, OffsetDateTime startDate,
                                                                 OffsetDateTime endDate) {
        ZoneId zone = zoneResolver.resolveZoneIdForSupply(supplyId.getId());
        OffsetDateTime effectiveEndDate = effectiveEndDate(startDate, endDate, zone, EnergyMetricsPeriodValidator.MAX_LENGTH);
        Supply supply = getSupplyOrThrow(supplyId);
        // The period is half-open, so every bucket below clips against the same pair the query
        // itself was bounded by.
        Instant rangeFrom = startDate.toInstant();
        Instant rangeToExclusive = effectiveEndDate.toInstant();

        return getDatadisConsumptionRepository.getDailyConsumptionsByRangeOfDates(supply, startDate, effectiveEndDate)
                .stream()
                .map(day -> priceDay(supply, day, zone, rangeFrom, rangeToExclusive))
                .toList();
    }

    @Override
    public List<SupplyConsumptionBucket> getMonthlySeriesBySupply(SupplyId supplyId, OffsetDateTime startDate,
                                                                   OffsetDateTime endDate) {
        ZoneId zone = zoneResolver.resolveZoneIdForSupply(supplyId.getId());
        OffsetDateTime effectiveEndDate = effectiveEndDate(startDate, endDate, zone, EnergyMetricsPeriodValidator.MAX_LENGTH);
        Supply supply = getSupplyOrThrow(supplyId);

        return getDatadisConsumptionRepository.getMonthlyConsumptionsByRangeOfDates(supply, startDate, effectiveEndDate)
                .stream()
                .map(consumption -> priceMonth(supply, consumption, zone))
                .toList();
    }

    /**
     * Checks the requested period against the same rules every energy metrics period obeys, before
     * anything is read, and returns the end actually read up to: the requested one, clipped to the
     * start of the current hour so that no bucket starts at or after an hour that has not happened.
     * {@code maxLength} is the longest period the series accepts.
     */
    private OffsetDateTime effectiveEndDate(OffsetDateTime startDate, OffsetDateTime endDate, ZoneId zone,
                                            Period maxLength) {
        OffsetDateTime startOfCurrentHour = EnergyMetricsPeriod.startOfCurrentHour(clock, zone);
        EnergyMetricsPeriodValidator.validate(startDate, endDate, startOfCurrentHour, zone, maxLength);
        return new EnergyMetricsPeriod(startDate, endDate).endingNoLaterThan(startOfCurrentHour).getEndDate();
    }

    /**
     * Prices a daily bucket over its own local day, clipped to the requested range.
     *
     * <p>The label is the bucket's local calendar day: the daily query carries a {@code tz()}
     * clause, so InfluxDB starts each group at local midnight and stamps the row with that
     * boundary. Rebuilding the day with {@code atStartOfDay}/{@code plusDays} rather than by adding
     * 24 hours is what makes a daylight saving day come out 23 or 25 hours long.
     *
     * <p>Clipping is correct here because the bucket's own energy is clipped the same way: the
     * query reads the hourly measurement between the requested bounds, so a partial edge bucket
     * reports the energy of exactly the hours this interval covers.
     *
     * <p>The supply has data that day when its records carrying self-consumption reach the
     * {@link PublicationThreshold publication threshold} of the day's hours inside the range.
     */
    private SupplyConsumptionBucket priceDay(Supply supply, DatadisDailyConsumption stored, ZoneId zone,
                                             Instant rangeFrom, Instant rangeToExclusive) {
        DatadisConsumption consumption = stored.getConsumption();
        LocalDate day = DateConverter.convertStringToLocalDate(consumption.getDate());
        Instant from = latest(day.atStartOfDay(zone).toInstant(), rangeFrom);
        Instant toExclusive = earliest(day.plusDays(1).atStartOfDay(zone).toInstant(), rangeToExclusive);
        long possibleHours = ChronoUnit.HOURS.between(from, toExclusive);
        boolean publishedData = stored.getPublishedHours() > 0
                && PublicationThreshold.isReached(stored.getPublishedHours(), possibleHours);

        return price(supply, consumption, publishedData, from, toExclusive);
    }

    /**
     * Prices an hourly bucket over its own hour, clipped to the end of the period.
     *
     * <p>The hour starts inside the period by construction, so only its end can need clipping: an end
     * that falls mid-hour leaves a partial last hour, priced over exactly the part its energy covers,
     * as the daily series prices a partial edge day.
     */
    private SupplyHourlyConsumptionBucket priceHour(Supply supply, DatadisConsumption consumption,
                                                    boolean publishedData, Instant hour, ZoneId zone,
                                                    Instant rangeToExclusive) {
        Instant toExclusive = earliest(hour.plus(1, ChronoUnit.HOURS), rangeToExclusive);
        return SupplyHourlyConsumptionBucket.of(hour.atZone(zone).toOffsetDateTime(),
                price(supply, consumption, publishedData, hour, toExclusive));
    }

    /**
     * Prices a monthly bucket over its <strong>whole</strong> local month, <strong>not</strong>
     * clipped to the requested range.
     *
     * <p>A monthly bucket is a pre-aggregate point stamped at local midnight of day 1. The range
     * selects it by that one instant and it then carries the entire month's energy, whatever the
     * bounds are -- a request ending mid-month still returns the full month. Clipping the priced
     * interval would therefore value a fraction of the month against the whole month's energy, and
     * the savings would not match the energy shown beside them.
     *
     * <p>{@code withDayOfMonth(1)} rather than trusting the label's day: it costs nothing and keeps
     * a point stamped at UTC midnight -- as pre-aggregates written before the local-calendar
     * alignment were -- resolving to the month it belongs to.
     */
    private SupplyConsumptionBucket priceMonth(Supply supply, DatadisConsumption consumption, ZoneId zone) {
        LocalDate firstDayOfMonth = DateConverter.convertStringToLocalDate(consumption.getDate())
                .withDayOfMonth(1);

        // The pre-aggregate keeps no count of the records carrying self-consumption: every stored
        // point counts as data.
        return price(supply, consumption, true,
                firstDayOfMonth.atStartOfDay(zone).toInstant(),
                firstDayOfMonth.plusMonths(1).atStartOfDay(zone).toInstant());
    }

    /**
     * Hands the bucket's already-summed self-consumption to the calculator, so a schedule of a
     * single segment -- which is every bucket that does not straddle a tariff change -- prices the
     * very figure reported beside it and issues no query at all. A bucket that does straddle one
     * still has its parts queried and priced segment by segment.
     */
    private SupplyConsumptionBucket price(Supply supply, DatadisConsumption consumption, boolean publishedData,
                                          Instant from, Instant toExclusive) {
        SupplySavings savings = supplySavingsCalculator.estimate(supply, from, toExclusive,
                selfConsumptionOf(consumption));
        return SupplyConsumptionBucket.of(consumption, savings, publishedData);
    }

    /**
     * The bucket's self-consumption as a double, read exactly as Datadis reported it.
     */
    private double selfConsumptionOf(DatadisConsumption consumption) {
        return DatadisEnergyValues.exactlyAsReported(consumption.getSelfConsumptionEnergyKWh()).doubleValue();
    }

    /**
     * An hour for which the supply stored no record, reported as the store reports an hour with no
     * record between two that have one: zero energy and no obtain method, labelled with the local
     * date and time the hour starts at.
     */
    private static DatadisConsumption nothingStored(Supply supply, Instant hour, ZoneId zone) {
        ZonedDateTime localStart = hour.atZone(zone);
        DatadisConsumption consumption = new DatadisConsumption();
        consumption.setCups(supply.getCode());
        consumption.setDate(DateTimeFormatter.ofPattern(DateConverter.DATE_FORMAT).format(localStart));
        consumption.setTime(DateTimeFormatter.ofPattern(DateConverter.TIME_FORMAT).format(localStart));
        consumption.setConsumptionKWh(0f);
        consumption.setSelfConsumptionEnergyKWh(0f);
        consumption.setSurplusEnergyKWh(0f);
        consumption.setGenerationEnergyKWh(0f);
        return consumption;
    }

    private static Instant latest(Instant left, Instant right) {
        return left.isAfter(right) ? left : right;
    }

    private static Instant earliest(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    private Supply getSupplyOrThrow(SupplyId supplyId) {
        Optional<Supply> supplyOptional = getSupplyRepository.findById(supplyId);
        if (supplyOptional.isEmpty()) {
            throw new SupplyNotFoundException(supplyId);
        }
        return supplyOptional.get();
    }
}
