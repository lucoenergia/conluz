package org.lucoenergia.conluz.infrastructure.consumption.savings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.tariff.DateRange;
import org.lucoenergia.conluz.domain.admin.supply.tariff.SupplyTariffResolver;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffPlan;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSchedule;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSegment;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TimeOfUsePlan;
import org.lucoenergia.conluz.domain.admin.supply.tariff.UnsupportedTariffPlanException;
import org.lucoenergia.conluz.domain.consumption.SupplySavings;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.consumption.savings.SupplySavingsCalculator;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the calculator's own contract, which differs from the energy-metrics service's in one
 * respect: its interval is a pair of instants, half-open, rather than a pair of
 * {@code OffsetDateTime}s with an inclusive end. The civil range it asks a resolver for therefore
 * has to be derived from the exclusive end, and getting that derivation wrong is silent -- it
 * under-prices the tail of the interval rather than failing -- so it is pinned here.
 *
 * <p>Expected amounts are computed by hand from the segment prices and the stubbed kWh, never read
 * off the implementation.
 */
class SupplySavingsCalculatorTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    private final GetDatadisConsumptionAggregateRepository aggregateRepository =
            mock(GetDatadisConsumptionAggregateRepository.class);
    private final SupplyTariffResolver tariffResolver = mock(SupplyTariffResolver.class);
    private final ZoneResolver zoneResolver = mock(ZoneResolver.class);

    private final SupplySavingsCalculator calculator =
            new SupplySavingsCalculatorImpl(aggregateRepository, tariffResolver, zoneResolver);

    private final Supply supply = SupplyMother.random().build();

    @BeforeEach
    void givenTheSupplyZoneIsMadrid() {
        when(zoneResolver.resolveZoneIdForSupply(supply.getId())).thenReturn(ZONE);
    }

    /**
     * Two segments, two prices, two different energies: neither the sum nor the selection can be
     * satisfied by picking one segment and ignoring the other.
     */
    @Test
    void eachSegmentIsPricedWithItsOwnTariff() {
        givenSegments(
                segment("2024-02-01", "2024-02-03", "0.10", TariffSource.ESTIMATE),
                segment("2024-02-03", "2024-02-05", "0.20", TariffSource.ESTIMATE));
        when(aggregateRepository.sumSelfConsumptionKWh(any(Supply.class),
                eq(madrid("2024-02-01T00:00+01:00")), eq(madrid("2024-02-03T00:00+01:00"))))
                .thenReturn(10d);
        when(aggregateRepository.sumSelfConsumptionKWh(any(Supply.class),
                eq(madrid("2024-02-03T00:00+01:00")), eq(madrid("2024-02-05T00:00+01:00"))))
                .thenReturn(20d);

        SupplySavings savings = calculator.estimate(supply,
                madrid("2024-02-01T00:00+01:00"), madrid("2024-02-05T00:00+01:00"));

        // 10 kWh * 0.10 + 20 kWh * 0.20 = 1.00 + 4.00 = 5.00
        assertEquals(0, new BigDecimal("5.00").compareTo(savings.getAmountEur()),
                () -> "amount was " + savings.getAmountEur());
    }

    /**
     * The interval's real bounds win over the schedule's: a resolver answers in whole civil days,
     * so the first and last segments routinely reach beyond the instants actually being priced.
     */
    @Test
    void eachSegmentIsClampedToTheRealInstantsOfTheInterval() {
        givenSegments(
                segment("2024-02-01", "2024-02-03", "0.10", TariffSource.ESTIMATE),
                segment("2024-02-03", "2024-02-05", "0.20", TariffSource.ESTIMATE));

        calculator.estimate(supply,
                madrid("2024-02-01T10:00+01:00"), madrid("2024-02-04T15:00+01:00"));

        verify(aggregateRepository).sumSelfConsumptionKWh(any(Supply.class),
                eq(madrid("2024-02-01T10:00+01:00")), eq(madrid("2024-02-03T00:00+01:00")));
        verify(aggregateRepository).sumSelfConsumptionKWh(any(Supply.class),
                eq(madrid("2024-02-03T00:00+01:00")), eq(madrid("2024-02-04T15:00+01:00")));
    }

    /**
     * The civil range must contain the interval's last included instant, which is the instant
     * before the exclusive end -- not the exclusive end itself. With an end of 11:00 on the 3rd,
     * asking for {@code [.., 2024-02-03)} would leave that whole day unpriced.
     */
    @Test
    void theResolverIsAskedForACivilRangeContainingTheWholeInterval() {
        givenASingleEstimatedSegmentAt("0.15");

        calculator.estimate(supply,
                madrid("2024-02-01T00:00+01:00"), madrid("2024-02-03T11:00+01:00"));

        assertEquals(new DateRange(LocalDate.parse("2024-02-01"), LocalDate.parse("2024-02-04")),
                capturedRange());
    }

    /**
     * An interval ending exactly at a civil midnight includes nothing of that day, so the range
     * must stop there rather than swallowing a day nobody asked about.
     */
    @Test
    void anIntervalEndingAtCivilMidnightDoesNotReachIntoTheFollowingDay() {
        givenASingleEstimatedSegmentAt("0.15");

        calculator.estimate(supply,
                madrid("2024-02-01T00:00+01:00"), madrid("2024-02-04T00:00+01:00"));

        assertEquals(new DateRange(LocalDate.parse("2024-02-01"), LocalDate.parse("2024-02-04")),
                capturedRange());
    }

    /**
     * The zone comes from the {@link ZoneResolver}, not from the offset the instants happen to be
     * formatted in, so two instants either side of local midnight land on the civil days the
     * community reads them as.
     */
    @Test
    void theCivilRangeIsReadInTheResolvedZoneRatherThanUtc() {
        givenASingleEstimatedSegmentAt("0.15");

        // 23:30Z on the 1st is 00:30 on the 2nd in Madrid; 23:30Z on the 2nd is 00:30 on the 3rd.
        calculator.estimate(supply,
                Instant.parse("2024-02-01T23:30:00Z"), Instant.parse("2024-02-02T23:30:00Z"));

        assertEquals(new DateRange(LocalDate.parse("2024-02-02"), LocalDate.parse("2024-02-04")),
                capturedRange());
    }

    /**
     * The overload carrying a known total exists precisely so a caller that has already summed the
     * interval does not pay for it twice.
     */
    @Test
    void aKnownTotalIsReusedForASingleSegmentInsteadOfIssuingAQuery() {
        givenASingleEstimatedSegmentAt("0.15");

        SupplySavings savings = calculator.estimate(supply,
                madrid("2024-02-01T00:00+01:00"), madrid("2024-02-02T00:00+01:00"), 53d);

        verify(aggregateRepository, never()).sumSelfConsumptionKWh(any(), any(), any());
        // 53 kWh * 0.15 = 7.95
        assertEquals(0, new BigDecimal("7.95").compareTo(savings.getAmountEur()),
                () -> "amount was " + savings.getAmountEur());
    }

    /**
     * Without a known total the same single segment has to be queried: the caller has given the
     * calculator nothing to reuse.
     */
    @Test
    void aSingleSegmentIsQueriedWhenNoTotalIsSupplied() {
        givenASingleEstimatedSegmentAt("0.15");
        when(aggregateRepository.sumSelfConsumptionKWh(any(Supply.class), any(), any())).thenReturn(53d);

        SupplySavings savings = calculator.estimate(supply,
                madrid("2024-02-01T00:00+01:00"), madrid("2024-02-02T00:00+01:00"));

        verify(aggregateRepository).sumSelfConsumptionKWh(any(Supply.class),
                eq(madrid("2024-02-01T00:00+01:00")), eq(madrid("2024-02-02T00:00+01:00")));
        assertEquals(0, new BigDecimal("7.95").compareTo(savings.getAmountEur()),
                () -> "amount was " + savings.getAmountEur());
    }

    /**
     * A known total says nothing about how the energy splits across segments, so it must not be
     * applied to a multi-segment schedule -- doing so would price the whole interval at every
     * segment's rate.
     */
    @Test
    void aKnownTotalIsIgnoredWhenTheScheduleHasSeveralSegments() {
        givenSegments(
                segment("2024-02-01", "2024-02-03", "0.10", TariffSource.ESTIMATE),
                segment("2024-02-03", "2024-02-05", "0.20", TariffSource.ESTIMATE));
        when(aggregateRepository.sumSelfConsumptionKWh(any(Supply.class), any(), any())).thenReturn(10d);

        SupplySavings savings = calculator.estimate(supply,
                madrid("2024-02-01T00:00+01:00"), madrid("2024-02-05T00:00+01:00"), 999d);

        // 10 * 0.10 + 10 * 0.20 = 3.00 -- the 999 is not used anywhere.
        assertEquals(0, new BigDecimal("3.00").compareTo(savings.getAmountEur()),
                () -> "amount was " + savings.getAmountEur());
    }

    @Test
    void vatIsAppliedOnTopOfTheEnergyTerm() {
        givenSegments(new TariffSegment(
                new DateRange(LocalDate.parse("2024-02-01"), LocalDate.parse("2024-02-02")),
                TariffPlan.flat(new BigDecimal("0.10")), new BigDecimal("0.21"), TariffSource.ESTIMATE));

        SupplySavings savings = calculator.estimate(supply,
                madrid("2024-02-01T00:00+01:00"), madrid("2024-02-02T00:00+01:00"), 100d);

        // 100 kWh * 0.10 * 1.21 = 12.10
        assertEquals(0, new BigDecimal("12.10").compareTo(savings.getAmountEur()),
                () -> "amount was " + savings.getAmountEur());
    }

    @Test
    void anIntervalWithoutSelfConsumptionIsWorthZero() {
        givenASingleEstimatedSegmentAt("0.15");

        SupplySavings savings = calculator.estimate(supply,
                madrid("2024-02-01T00:00+01:00"), madrid("2024-02-02T00:00+01:00"), 0d);

        assertEquals(0, BigDecimal.ZERO.compareTo(savings.getAmountEur()));
        assertEquals(TariffSource.ESTIMATE, savings.getTariffSource());
    }

    @Test
    void aScheduleOfOnlyRealSegmentsReportsARealTariffSource() {
        givenSegments(
                segment("2024-02-01", "2024-02-03", "0.10", TariffSource.REAL_TARIFF),
                segment("2024-02-03", "2024-02-05", "0.20", TariffSource.REAL_TARIFF));

        SupplySavings savings = calculator.estimate(supply,
                madrid("2024-02-01T00:00+01:00"), madrid("2024-02-05T00:00+01:00"));

        assertEquals(TariffSource.REAL_TARIFF, savings.getTariffSource());
    }

    /**
     * A total is only as trustworthy as its least trustworthy part, and the estimated segment is
     * placed second here so the answer cannot come from reading the first segment alone.
     */
    @Test
    void oneEstimatedSegmentMakesTheWholeAmountAnEstimate() {
        givenSegments(
                segment("2024-02-01", "2024-02-03", "0.10", TariffSource.REAL_TARIFF),
                segment("2024-02-03", "2024-02-05", "0.20", TariffSource.ESTIMATE));

        SupplySavings savings = calculator.estimate(supply,
                madrid("2024-02-01T00:00+01:00"), madrid("2024-02-05T00:00+01:00"));

        assertEquals(TariffSource.ESTIMATE, savings.getTariffSource());
    }

    @Test
    void aPlanThatCannotBePricedIsRejectedRatherThanSilentlySkipped() {
        givenSegments(new TariffSegment(
                new DateRange(LocalDate.parse("2024-02-01"), LocalDate.parse("2024-02-02")),
                new TimeOfUsePlan(), BigDecimal.ZERO, TariffSource.ESTIMATE));

        UnsupportedTariffPlanException exception = assertThrows(UnsupportedTariffPlanException.class,
                () -> calculator.estimate(supply,
                        madrid("2024-02-01T00:00+01:00"), madrid("2024-02-02T00:00+01:00"), 10d));

        assertEquals(TimeOfUsePlan.class, exception.getPlanType());
    }

    /**
     * A segment whose clamped interval collapses to nothing contributes nothing and costs no
     * query, rather than being asked for an empty range.
     */
    @Test
    void aSegmentThatFallsOutsideTheIntervalIsNotQueried() {
        givenSegments(
                segment("2024-02-01", "2024-02-02", "0.10", TariffSource.ESTIMATE),
                segment("2024-02-02", "2024-02-05", "0.20", TariffSource.ESTIMATE));
        when(aggregateRepository.sumSelfConsumptionKWh(any(Supply.class), any(), any())).thenReturn(10d);

        // The interval covers only the first segment's day.
        calculator.estimate(supply,
                madrid("2024-02-01T00:00+01:00"), madrid("2024-02-02T00:00+01:00"));

        verify(aggregateRepository).sumSelfConsumptionKWh(any(Supply.class),
                eq(madrid("2024-02-01T00:00+01:00")), eq(madrid("2024-02-02T00:00+01:00")));
        verify(aggregateRepository, never()).sumSelfConsumptionKWh(any(Supply.class),
                eq(madrid("2024-02-02T00:00+01:00")), eq(madrid("2024-02-05T00:00+01:00")));
    }

    private void givenASingleEstimatedSegmentAt(String pricePerKwh) {
        // Stands in for the only resolver shipping today: one estimated flat segment covering
        // exactly the range it was asked for.
        when(tariffResolver.scheduleFor(eq(SupplyId.of(supply.getId())), any(DateRange.class)))
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

    private TariffSegment segment(String startIso, String endExclusiveIso, String pricePerKwh,
                                  TariffSource source) {
        return new TariffSegment(
                new DateRange(LocalDate.parse(startIso), LocalDate.parse(endExclusiveIso)),
                TariffPlan.flat(new BigDecimal(pricePerKwh)),
                BigDecimal.ZERO,
                source);
    }

    private DateRange capturedRange() {
        ArgumentCaptor<DateRange> range = ArgumentCaptor.forClass(DateRange.class);
        verify(tariffResolver).scheduleFor(eq(SupplyId.of(supply.getId())), range.capture());
        return range.getValue();
    }

    private static Instant madrid(String offsetDateTime) {
        return OffsetDateTime.parse(offsetDateTime).toInstant();
    }

    /**
     * Guards the assumption the clamping relies on: a civil date's start-of-day in the resolved
     * zone is the instant the tests spell out by offset.
     */
    @Test
    void theZoneUsedForClampingIsTheResolvedOne() {
        assertTrue(LocalDate.parse("2024-02-01").atStartOfDay(ZONE).toInstant()
                .equals(madrid("2024-02-01T00:00+01:00")));
    }
}
