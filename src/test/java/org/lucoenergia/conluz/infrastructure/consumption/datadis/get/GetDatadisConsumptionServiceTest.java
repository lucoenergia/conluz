package org.lucoenergia.conluz.infrastructure.consumption.datadis.get;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.tariff.DateRange;
import org.lucoenergia.conluz.domain.admin.supply.tariff.SupplyTariffResolver;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffPlan;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSchedule;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSegment;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;
import org.lucoenergia.conluz.domain.consumption.SupplyConsumptionBucket;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionService;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.consumption.savings.SupplySavingsCalculatorImpl;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The per-bucket half of the savings feature: which instant interval each bucket of a series is
 * priced over, and how many self-consumption queries that costs.
 *
 * <p>Runs the <em>real</em> {@link SupplySavingsCalculatorImpl} over a stubbed resolver rather than
 * mocking the calculator, because what is under test is precisely the pair of instants handed to it
 * -- a mock would happily accept a wrong interval. Expected amounts are computed by hand from the
 * stubbed prices and energies.
 */
class GetDatadisConsumptionServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    private final GetDatadisConsumptionRepository consumptionRepository =
            mock(GetDatadisConsumptionRepository.class);
    private final GetSupplyRepository getSupplyRepository = mock(GetSupplyRepository.class);
    private final GetDatadisConsumptionAggregateRepository aggregateRepository =
            mock(GetDatadisConsumptionAggregateRepository.class);
    private final SupplyTariffResolver tariffResolver = mock(SupplyTariffResolver.class);
    private final ZoneResolver zoneResolver = mock(ZoneResolver.class);

    private final GetDatadisConsumptionService service = new GetDatadisConsumptionServiceImpl(
            consumptionRepository,
            getSupplyRepository,
            new SupplySavingsCalculatorImpl(aggregateRepository, tariffResolver, zoneResolver),
            zoneResolver);

    private final Supply supply = SupplyMother.random().build();

    @BeforeEach
    void givenTheSupplyExistsInMadrid() {
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));
        when(zoneResolver.resolveZoneIdForSupply(supply.getId())).thenReturn(ZONE);
    }

    /**
     * Three days, three different energies: neither the per-bucket pricing nor the ordering can be
     * satisfied by pricing one bucket and copying the result.
     */
    @Test
    void eachDailyBucketIsPricedFromItsOwnEnergy() {
        givenASingleSegmentAt("0.15");
        givenDailyBuckets(
                bucket("2024/02/05", 4.0),
                bucket("2024/02/06", 0.0),
                bucket("2024/02/07", 2.5));

        List<SupplyConsumptionBucket> series = service.getDailySeriesBySupply(SupplyId.of(supply.getId()),
                madridOffset("2024-02-05T00:00+01:00"), madridOffset("2024-02-07T23:59:59+01:00"));

        // 4.0 * 0.15 = 0.60, 0.0 * 0.15 = 0, 2.5 * 0.15 = 0.375
        assertEquals(List.of("0.6", "0", "0.375"), amountsOf(series));
    }

    /**
     * AC8. A whole local month of daily buckets under one tariff segment: the calculator is handed
     * each bucket's own already-summed self-consumption, so the aggregate repository is never
     * touched -- one series query for the request, and nothing per bucket.
     */
    @Test
    void aDailyMonthUnderASingleSegmentIssuesNoSelfConsumptionQuery() {
        givenASingleSegmentAt("0.15");
        List<DatadisConsumption> february = new ArrayList<>();
        for (int day = 1; day <= 29; day++) {
            february.add(bucket(String.format("2024/02/%02d", day), 0.25 * day));
        }
        givenDailyBuckets(february.toArray(new DatadisConsumption[0]));

        List<SupplyConsumptionBucket> series = service.getDailySeriesBySupply(SupplyId.of(supply.getId()),
                madridOffset("2024-02-01T00:00+01:00"), madridOffset("2024-02-29T23:59:59+01:00"));

        assertEquals(29, series.size());
        verify(aggregateRepository, never()).sumSelfConsumptionKWh(any(Supply.class), any(), any());
    }

    /**
     * AC7. A tariff change inside a monthly bucket: the month's total says nothing about how its
     * energy is spread across the two prices, so each part is queried and priced on its own.
     */
    @Test
    void aMonthlyBucketStraddlingATariffChangeIsPricedPerSegment() {
        givenSegments(
                segment("2024-02-01", "2024-02-15", "0.10"),
                segment("2024-02-15", "2024-03-01", "0.20"));
        givenMonthlyBuckets(bucket("2024/02/01", 100.0), bucket("2024/03/01", 10.0));
        when(aggregateRepository.sumSelfConsumptionKWh(any(Supply.class),
                eq(madrid("2024-02-01T00:00+01:00")), eq(madrid("2024-02-15T00:00+01:00"))))
                .thenReturn(40d);
        when(aggregateRepository.sumSelfConsumptionKWh(any(Supply.class),
                eq(madrid("2024-02-15T00:00+01:00")), eq(madrid("2024-03-01T00:00+01:00"))))
                .thenReturn(60d);

        List<SupplyConsumptionBucket> series = service.getMonthlySeriesBySupply(SupplyId.of(supply.getId()),
                madridOffset("2024-02-01T00:00+01:00"), madridOffset("2024-03-31T23:59:59+02:00"));

        // February: 40 kWh * 0.10 + 60 kWh * 0.20 = 4.00 + 12.00 = 16.00
        assertEquals(0, new BigDecimal("16.00").compareTo(series.get(0).getSavings().getAmountEur()),
                () -> "February was priced at " + series.get(0).getSavings().getAmountEur());
        verify(aggregateRepository).sumSelfConsumptionKWh(any(Supply.class),
                eq(madrid("2024-02-01T00:00+01:00")), eq(madrid("2024-02-15T00:00+01:00")));
        verify(aggregateRepository).sumSelfConsumptionKWh(any(Supply.class),
                eq(madrid("2024-02-15T00:00+01:00")), eq(madrid("2024-03-01T00:00+01:00")));
    }

    /**
     * A monthly bucket is a pre-aggregate carrying the whole month's energy: the request selects it
     * by the single instant it is stamped at and never trims it. Its priced interval must therefore
     * be the whole local month too, mid-month bounds included -- clipping would value a fraction of
     * the month against the full month's energy.
     */
    @Test
    void aMonthlyBucketIsPricedOverTheWholeMonthEvenWithMidMonthBounds() {
        givenSegments(
                segment("2024-02-01", "2024-02-15", "0.10"),
                segment("2024-02-15", "2024-03-01", "0.20"));
        givenMonthlyBuckets(bucket("2024/02/01", 100.0));

        service.getMonthlySeriesBySupply(SupplyId.of(supply.getId()),
                madridOffset("2024-02-10T00:00+01:00"), madridOffset("2024-02-20T23:59:59+01:00"));

        verify(aggregateRepository).sumSelfConsumptionKWh(any(Supply.class),
                eq(madrid("2024-02-01T00:00+01:00")), eq(madrid("2024-02-15T00:00+01:00")));
        verify(aggregateRepository).sumSelfConsumptionKWh(any(Supply.class),
                eq(madrid("2024-02-15T00:00+01:00")), eq(madrid("2024-03-01T00:00+01:00")));
    }

    /**
     * Daily buckets, unlike monthly ones, <em>are</em> clipped: the query reads the hourly
     * measurement between the requested bounds, so an edge bucket's energy covers only the hours
     * asked for and its pricing must cover exactly those.
     */
    @Test
    void theEdgeDailyBucketsArePricedOnlyOverTheHoursRequested() {
        givenSegments(
                segment("2024-02-01", "2024-02-11", "0.10"),
                segment("2024-02-11", "2024-03-01", "0.20"));
        givenDailyBuckets(bucket("2024/02/10", 3.0), bucket("2024/02/11", 5.0));

        service.getDailySeriesBySupply(SupplyId.of(supply.getId()),
                madridOffset("2024-02-10T12:00+01:00"), madridOffset("2024-02-11T11:59:59+01:00"));

        verify(aggregateRepository).sumSelfConsumptionKWh(any(Supply.class),
                eq(madrid("2024-02-10T12:00+01:00")), eq(madrid("2024-02-11T00:00+01:00")));
        verify(aggregateRepository).sumSelfConsumptionKWh(any(Supply.class),
                eq(madrid("2024-02-11T00:00+01:00")),
                eq(madrid("2024-02-11T11:59:59+01:00").plusNanos(1)));
    }

    /**
     * The local day the label names, not 24 hours added to its start: on the spring transition the
     * two differ by an hour, and pricing the difference would silently reach into the next day.
     */
    @Test
    void aSpringForwardDayIsPricedOverTwentyThreeHours() {
        givenSegments(
                segment("2024-03-01", "2024-03-31", "0.10"),
                segment("2024-03-31", "2024-04-01", "0.20"));
        givenDailyBuckets(bucket("2024/03/31", 6.0));

        service.getDailySeriesBySupply(SupplyId.of(supply.getId()),
                madridOffset("2024-03-31T00:00+01:00"), madridOffset("2024-04-01T00:00+02:00"));

        assertEquals(Duration.ofHours(23), pricedWindow());
    }

    @Test
    void anAutumnFallBackDayIsPricedOverTwentyFiveHours() {
        givenSegments(
                segment("2024-10-01", "2024-10-27", "0.10"),
                segment("2024-10-27", "2024-11-01", "0.20"));
        givenDailyBuckets(bucket("2024/10/27", 6.0));

        service.getDailySeriesBySupply(SupplyId.of(supply.getId()),
                madridOffset("2024-10-27T00:00+02:00"), madridOffset("2024-10-28T00:00+01:00"));

        assertEquals(Duration.ofHours(25), pricedWindow());
    }

    /**
     * A monthly bucket spanning the autumn transition is 25 hours longer than 30 days, for the same
     * reason -- and the month is derived from the label's first day, not from a fixed length.
     */
    @Test
    void aMonthlyBucketCoversTheWholeLocalMonthAcrossADaylightSavingTransition() {
        givenSegments(
                segment("2024-09-01", "2024-10-01", "0.10"),
                segment("2024-10-01", "2024-11-01", "0.20"));
        givenMonthlyBuckets(bucket("2024/10/01", 200.0));

        service.getMonthlySeriesBySupply(SupplyId.of(supply.getId()),
                madridOffset("2024-10-01T00:00+02:00"), madridOffset("2024-10-01T00:00+02:00"));

        verify(aggregateRepository).sumSelfConsumptionKWh(any(Supply.class),
                eq(madrid("2024-10-01T00:00+02:00")), eq(madrid("2024-11-01T00:00+01:00")));
    }

    /**
     * Two supplies, two schedules: the schedule is resolved for the supply being priced, not for
     * whichever one happened to be asked about first.
     */
    @Test
    void eachSupplyIsPricedWithItsOwnSchedule() {
        Supply otherSupply = SupplyMother.random().build();
        when(getSupplyRepository.findById(SupplyId.of(otherSupply.getId()))).thenReturn(Optional.of(otherSupply));
        when(zoneResolver.resolveZoneIdForSupply(otherSupply.getId())).thenReturn(ZONE);
        givenASingleSegmentAt(SupplyId.of(supply.getId()), "0.15");
        givenASingleSegmentAt(SupplyId.of(otherSupply.getId()), "0.30");
        when(consumptionRepository.getDailyConsumptionsByRangeOfDates(eq(supply), any(), any()))
                .thenReturn(List.of(bucket("2024/02/05", 4.0), bucket("2024/02/06", 2.0)));
        when(consumptionRepository.getDailyConsumptionsByRangeOfDates(eq(otherSupply), any(), any()))
                .thenReturn(List.of(bucket("2024/02/05", 4.0), bucket("2024/02/06", 2.0)));

        OffsetDateTime from = madridOffset("2024-02-05T00:00+01:00");
        OffsetDateTime to = madridOffset("2024-02-06T23:59:59+01:00");

        // 4.0 and 2.0 kWh at 0.15 against the same two at 0.30.
        assertEquals(List.of("0.6", "0.3"),
                amountsOf(service.getDailySeriesBySupply(SupplyId.of(supply.getId()), from, to)));
        assertEquals(List.of("1.2", "0.6"),
                amountsOf(service.getDailySeriesBySupply(SupplyId.of(otherSupply.getId()), from, to)));
    }

    /**
     * The bucket travels through untouched: the series carries the energy fields the repository
     * produced, with the savings alongside them rather than in place of them.
     */
    @Test
    void theBucketKeepsTheEnergyItWasBuiltFrom() {
        givenASingleSegmentAt("0.15");
        DatadisConsumption first = bucket("2024/02/05", 4.0);
        DatadisConsumption second = bucket("2024/02/06", 2.0);
        givenDailyBuckets(first, second);

        List<SupplyConsumptionBucket> series = service.getDailySeriesBySupply(SupplyId.of(supply.getId()),
                madridOffset("2024-02-05T00:00+01:00"), madridOffset("2024-02-06T23:59:59+01:00"));

        assertEquals(List.of(first, second), series.stream().map(SupplyConsumptionBucket::getConsumption).toList());
        assertEquals(List.of(TariffSource.ESTIMATE, TariffSource.ESTIMATE),
                series.stream().map(item -> item.getSavings().getTariffSource()).toList());
    }

    /**
     * Amounts as their shortest exact decimal: the calculator leaves them unrounded, and their
     * scale is an artefact of how many segments were multiplied, not part of what is under test.
     */
    private List<String> amountsOf(List<SupplyConsumptionBucket> series) {
        return series.stream()
                .map(item -> item.getSavings().getAmountEur().stripTrailingZeros().toPlainString())
                .toList();
    }

    /**
     * The length of the single interval the aggregate repository was asked about. Only meaningful
     * where exactly one segment overlaps the bucket, which is how the callers below set it up.
     */
    private Duration pricedWindow() {
        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(aggregateRepository).sumSelfConsumptionKWh(any(Supply.class), from.capture(), to.capture());
        return Duration.between(from.getValue(), to.getValue());
    }

    private void givenDailyBuckets(DatadisConsumption... buckets) {
        when(consumptionRepository.getDailyConsumptionsByRangeOfDates(any(Supply.class), any(), any()))
                .thenReturn(List.of(buckets));
    }

    private void givenMonthlyBuckets(DatadisConsumption... buckets) {
        when(consumptionRepository.getMonthlyConsumptionsByRangeOfDates(any(Supply.class), any(), any()))
                .thenReturn(List.of(buckets));
    }

    private void givenASingleSegmentAt(String pricePerKwh) {
        givenASingleSegmentAt(SupplyId.of(supply.getId()), pricePerKwh);
    }

    /**
     * Stands in for the only resolver shipping today: one estimated flat segment covering exactly
     * the civil range it was asked for, whatever that range is.
     */
    private void givenASingleSegmentAt(SupplyId supplyId, String pricePerKwh) {
        when(tariffResolver.scheduleFor(eq(supplyId), any(DateRange.class)))
                .thenAnswer(invocation -> new TariffSchedule(List.of(new TariffSegment(
                        invocation.getArgument(1),
                        TariffPlan.flat(new BigDecimal(pricePerKwh)),
                        BigDecimal.ZERO,
                        TariffSource.ESTIMATE))));
    }

    private void givenSegments(TariffSegment... segments) {
        when(tariffResolver.scheduleFor(eq(SupplyId.of(supply.getId())), any(DateRange.class)))
                .thenReturn(new TariffSchedule(List.of(segments)));
    }

    private TariffSegment segment(String startIso, String endExclusiveIso, String pricePerKwh) {
        return new TariffSegment(
                new DateRange(LocalDate.parse(startIso), LocalDate.parse(endExclusiveIso)),
                TariffPlan.flat(new BigDecimal(pricePerKwh)),
                BigDecimal.ZERO,
                TariffSource.ESTIMATE);
    }

    private DatadisConsumption bucket(String date, double selfConsumptionKWh) {
        DatadisConsumption consumption = new DatadisConsumption();
        consumption.setCups(supply.getCode());
        consumption.setDate(date);
        consumption.setTime("00:00");
        consumption.setConsumptionKWh(0f);
        consumption.setSurplusEnergyKWh(0f);
        consumption.setGenerationEnergyKWh(0f);
        consumption.setSelfConsumptionEnergyKWh((float) selfConsumptionKWh);
        return consumption;
    }

    private static Instant madrid(String offsetDateTime) {
        return OffsetDateTime.parse(offsetDateTime).toInstant();
    }

    private static OffsetDateTime madridOffset(String offsetDateTime) {
        return OffsetDateTime.parse(offsetDateTime);
    }
}
