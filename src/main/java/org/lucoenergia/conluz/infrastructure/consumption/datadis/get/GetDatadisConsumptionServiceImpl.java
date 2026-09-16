package org.lucoenergia.conluz.infrastructure.consumption.datadis.get;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.SupplyConsumptionBucket;
import org.lucoenergia.conluz.domain.consumption.SupplySavings;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionService;
import org.lucoenergia.conluz.domain.consumption.savings.SupplySavingsCalculator;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class GetDatadisConsumptionServiceImpl implements GetDatadisConsumptionService {

    private final GetDatadisConsumptionRepository getDatadisConsumptionRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final SupplySavingsCalculator supplySavingsCalculator;
    private final ZoneResolver zoneResolver;

    public GetDatadisConsumptionServiceImpl(
            @Qualifier("getDatadisConsumptionRepositoryInflux") GetDatadisConsumptionRepository getDatadisConsumptionRepository,
            GetSupplyRepository getSupplyRepository,
            SupplySavingsCalculator supplySavingsCalculator,
            ZoneResolver zoneResolver) {
        this.getDatadisConsumptionRepository = getDatadisConsumptionRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.supplySavingsCalculator = supplySavingsCalculator;
        this.zoneResolver = zoneResolver;
    }

    @Override
    public List<DatadisConsumption> getHourlyConsumptionBySupply(SupplyId supplyId, OffsetDateTime startDate,
                                                                 OffsetDateTime endDate) {
        Supply supply = getSupplyOrThrow(supplyId);
        return getDatadisConsumptionRepository.getHourlyConsumptionsByRangeOfDates(supply, startDate, endDate);
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
        Supply supply = getSupplyOrThrow(supplyId);
        ZoneId zone = zoneResolver.resolveZoneIdForSupply(supply.getId());
        // The public end is inclusive; it becomes an exclusive instant here and only here, so every
        // bucket below clips against the same pair the query itself was bounded by.
        Instant rangeFrom = startDate.toInstant();
        Instant rangeToExclusive = DateConverter.toExclusiveUpperBound(endDate);

        return getDatadisConsumptionRepository.getDailyConsumptionsByRangeOfDates(supply, startDate, endDate)
                .stream()
                .map(consumption -> priceDay(supply, consumption, zone, rangeFrom, rangeToExclusive))
                .toList();
    }

    @Override
    public List<SupplyConsumptionBucket> getMonthlySeriesBySupply(SupplyId supplyId, OffsetDateTime startDate,
                                                                   OffsetDateTime endDate) {
        Supply supply = getSupplyOrThrow(supplyId);
        ZoneId zone = zoneResolver.resolveZoneIdForSupply(supply.getId());

        return getDatadisConsumptionRepository.getMonthlyConsumptionsByRangeOfDates(supply, startDate, endDate)
                .stream()
                .map(consumption -> priceMonth(supply, consumption, zone))
                .toList();
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
     */
    private SupplyConsumptionBucket priceDay(Supply supply, DatadisConsumption consumption, ZoneId zone,
                                             Instant rangeFrom, Instant rangeToExclusive) {
        LocalDate day = DateConverter.convertStringToLocalDate(consumption.getDate());
        Instant from = latest(day.atStartOfDay(zone).toInstant(), rangeFrom);
        Instant toExclusive = earliest(day.plusDays(1).atStartOfDay(zone).toInstant(), rangeToExclusive);

        return price(supply, consumption, from, toExclusive);
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

        return price(supply, consumption,
                firstDayOfMonth.atStartOfDay(zone).toInstant(),
                firstDayOfMonth.plusMonths(1).atStartOfDay(zone).toInstant());
    }

    /**
     * Hands the bucket's already-summed self-consumption to the calculator, so a schedule of a
     * single segment -- which is every bucket that does not straddle a tariff change -- prices the
     * very figure reported beside it and issues no query at all. A bucket that does straddle one
     * still has its parts queried and priced segment by segment.
     */
    private SupplyConsumptionBucket price(Supply supply, DatadisConsumption consumption, Instant from,
                                          Instant toExclusive) {
        SupplySavings savings = supplySavingsCalculator.estimate(supply, from, toExclusive,
                selfConsumptionOf(consumption));
        return SupplyConsumptionBucket.of(consumption, savings);
    }

    /**
     * The bucket's self-consumption as a double. Widening the float directly would carry its binary
     * error into digits the caller never sees -- {@code 2.37f} widens to {@code 2.3700001239776611}
     * -- so the value is read back through the float's shortest decimal representation, which is
     * the very text the response carries.
     */
    private double selfConsumptionOf(DatadisConsumption consumption) {
        Float selfConsumption = consumption.getSelfConsumptionEnergyKWh();
        return selfConsumption == null ? 0d : Double.parseDouble(selfConsumption.toString());
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
