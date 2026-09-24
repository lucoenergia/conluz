package org.lucoenergia.conluz.infrastructure.consumption.datadis.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.tariff.DateRange;
import org.lucoenergia.conluz.domain.admin.supply.tariff.SupplyTariffResolver;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffPlan;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSchedule;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSegment;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TimeOfUsePlan;
import org.lucoenergia.conluz.domain.admin.supply.tariff.UnsupportedTariffPlanException;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DatadisConsumptionAggregate;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.consumption.GetSupplyEnergyMetricsService;
import org.lucoenergia.conluz.domain.consumption.InvalidEnergyMetricsPeriodException;
import org.lucoenergia.conluz.domain.consumption.RecordedConsumptionPeriod;
import org.lucoenergia.conluz.domain.consumption.SupplyEnergyMetrics;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.consumption.GetSupplyEnergyMetricsServiceImpl;
import org.lucoenergia.conluz.infrastructure.consumption.savings.SupplySavingsCalculatorImpl;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the period resolution, validation and hour arithmetic the service owns. The
 * store is mocked; the aggregation itself is covered by
 * {@link GetDatadisConsumptionAggregateRepositoryInfluxIntegrationTest} against a real InfluxDB.
 *
 * <p>The real {@link DateConverter} is used over a mocked {@link TimeConfiguration}, so the zone
 * the service resolves an unbounded period through is the configured one rather than the JVM
 * default.</p>
 */
class GetSupplyEnergyMetricsServiceTest {

    private static final OffsetDateTime START_DATE = OffsetDateTime.parse("2024-02-01T00:00:00+01:00");
    private static final OffsetDateTime END_DATE = OffsetDateTime.parse("2024-02-01T23:00:00+01:00");

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    private final GetDatadisConsumptionAggregateRepository aggregateRepository =
            mock(GetDatadisConsumptionAggregateRepository.class);
    private final GetSupplyRepository getSupplyRepository = mock(GetSupplyRepository.class);
    private final TimeConfiguration timeConfiguration = mock(TimeConfiguration.class);
    private final SupplyTariffResolver tariffResolver = mock(SupplyTariffResolver.class);
    private final ZoneResolver zoneResolver = mock(ZoneResolver.class);
    private final GetSupplyEnergyMetricsService service = new GetSupplyEnergyMetricsServiceImpl(
            aggregateRepository, getSupplyRepository, new DateConverter(timeConfiguration),
            new SupplySavingsCalculatorImpl(aggregateRepository, tariffResolver, zoneResolver));

    private final Supply supply = SupplyMother.random().build();
    private final SupplyId supplyId = SupplyId.of(supply.getId());

    /**
     * Every resolved period is priced, so the tests that predate savings still need a tariff to
     * be resolvable. The default stands in for the only resolver shipping today: one estimated
     * flat segment covering exactly the range it was asked for.
     */
    @BeforeEach
    void givenATariffIsResolvable() {
        when(zoneResolver.resolveZoneIdForSupply(supply.getId())).thenReturn(ZONE);
        givenASingleEstimatedSegmentAt("0.15");
    }

    @Test
    void supplyingOnlyTheStartDateIsRejectedAsAnIncompletePeriod() {
        InvalidEnergyMetricsPeriodException exception = assertThrows(InvalidEnergyMetricsPeriodException.class,
                () -> service.getEnergyMetrics(supplyId, START_DATE, null));

        assertEquals(InvalidEnergyMetricsPeriodException.Reason.INCOMPLETE_PERIOD, exception.getReason());
    }

    @Test
    void supplyingOnlyTheEndDateIsRejectedAsAnIncompletePeriod() {
        InvalidEnergyMetricsPeriodException exception = assertThrows(InvalidEnergyMetricsPeriodException.class,
                () -> service.getEnergyMetrics(supplyId, null, END_DATE));

        assertEquals(InvalidEnergyMetricsPeriodException.Reason.INCOMPLETE_PERIOD, exception.getReason());
    }

    @Test
    void aStartDateAfterTheEndDateIsRejected() {
        InvalidEnergyMetricsPeriodException exception = assertThrows(InvalidEnergyMetricsPeriodException.class,
                () -> service.getEnergyMetrics(supplyId, END_DATE, START_DATE));

        assertEquals(InvalidEnergyMetricsPeriodException.Reason.START_AFTER_END, exception.getReason());
    }

    @Test
    void aPeriodOfASingleInstantIsAccepted() {
        givenSupplyExists();
        givenAggregate(new DatadisConsumptionAggregate(1d, 0d, 0d, 1L));

        SupplyEnergyMetrics metrics = service.getEnergyMetrics(supplyId, START_DATE, START_DATE);

        assertEquals(1L, metrics.getExpectedHours());
    }

    /**
     * An unusable period is rejected before anything is read, so a bad request costs no query.
     */
    @Test
    void anInvalidPeriodIsRejectedBeforeAnythingIsRead() {
        assertThrows(InvalidEnergyMetricsPeriodException.class,
                () -> service.getEnergyMetrics(supplyId, END_DATE, START_DATE));

        verifyNoInteractions(getSupplyRepository);
        verifyNoInteractions(aggregateRepository);
    }

    @Test
    void anUnknownSupplyIsReportedAsNotFoundWithoutQueryingTheStore() {
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class,
                () -> service.getEnergyMetrics(supplyId, START_DATE, END_DATE));

        verifyNoInteractions(aggregateRepository);
    }

    @Test
    void anExplicitPeriodIsPassedToTheStoreUnchanged() {
        givenSupplyExists();
        givenAggregate(new DatadisConsumptionAggregate(102d, 53d, 11.5d, 3L));

        SupplyEnergyMetrics metrics = service.getEnergyMetrics(supplyId, START_DATE, END_DATE);

        ArgumentCaptor<OffsetDateTime> start = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> end = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(aggregateRepository).aggregateByRangeOfDates(any(Supply.class), start.capture(), end.capture());

        assertEquals(START_DATE, start.getValue());
        assertEquals(END_DATE, end.getValue());
        assertEquals(START_DATE, metrics.getStartDate());
        assertEquals(END_DATE, metrics.getEndDate());
        // The supply must travel with the metrics; the controller has no other way to name it.
        assertSame(supply, metrics.getSupply());
        // The aggregate's totals reach the result untouched.
        assertEquals(102d, metrics.getGridImportKWh());
        assertEquals(53d, metrics.getSelfConsumptionKWh());
        assertEquals(11.5d, metrics.getSurplusKWh());
        assertEquals(3L, metrics.getHoursWithData());
        // Never queried when the period is explicit.
        verify(aggregateRepository, never()).findRecordedPeriod(any(Supply.class));
    }

    @Test
    void anUnboundedPeriodSpansTheStoredRecordsInTheConfiguredZone() {
        givenSupplyExists();
        givenConfiguredZoneIsMadrid();
        when(aggregateRepository.findRecordedPeriod(supply)).thenReturn(Optional.of(
                new RecordedConsumptionPeriod(
                        Instant.parse("2024-01-31T23:00:00Z"),
                        Instant.parse("2024-02-01T22:00:00Z"))));
        givenAggregate(new DatadisConsumptionAggregate(4d, 1d, 0d, 24L));

        SupplyEnergyMetrics metrics = service.getEnergyMetrics(supplyId, null, null);

        // Europe/Madrid in February, so the stored instants surface as +01:00, not as UTC.
        assertEquals(OffsetDateTime.parse("2024-02-01T00:00:00+01:00"), metrics.getStartDate());
        assertEquals(OffsetDateTime.parse("2024-02-01T23:00:00+01:00"), metrics.getEndDate());
        assertEquals(ZoneOffset.ofHours(1), metrics.getStartDate().getOffset());
        assertEquals(24L, metrics.getExpectedHours());
    }

    @Test
    void aSupplyWithNoStoredRecordYieldsAnEmptyResultWithoutAggregating() {
        givenSupplyExists();
        when(aggregateRepository.findRecordedPeriod(supply)).thenReturn(Optional.empty());

        SupplyEnergyMetrics metrics = service.getEnergyMetrics(supplyId, null, null);

        assertNull(metrics.getStartDate());
        assertNull(metrics.getEndDate());
        assertEquals(0L, metrics.getHoursWithData());
        assertEquals(0L, metrics.getExpectedHours());
        assertEquals(0d, metrics.getTotalConsumptionKWh());
        assertNull(metrics.getSelfSufficiencyRatio());
        assertNull(metrics.getSelfConsumptionRatio());
        assertSame(supply, metrics.getSupply());
        // There is no period to aggregate over, so the store is not asked for one.
        verify(aggregateRepository, never()).aggregateByRangeOfDates(any(Supply.class), any(), any());
    }

    @Test
    void expectedHoursCountsTheHourlySlotsOfAFullDay() {
        assertEquals(24L, expectedHoursBetween("2024-02-01T00:00:00+01:00", "2024-02-01T23:00:00+01:00"));
    }

    /**
     * Europe/Madrid on 2023-03-26 loses an hour, so a full local day is 23 hourly slots.
     */
    @Test
    void expectedHoursShrinksOnTheSpringDaylightSavingTransition() {
        assertEquals(23L, expectedHoursBetween("2023-03-26T00:00:00+01:00", "2023-03-26T23:00:00+02:00"));
    }

    /**
     * Europe/Madrid on 2023-10-29 repeats an hour, so a full local day is 25 hourly slots.
     */
    @Test
    void expectedHoursGrowsOnTheAutumnDaylightSavingTransition() {
        assertEquals(25L, expectedHoursBetween("2023-10-29T00:00:00+02:00", "2023-10-29T23:00:00+01:00"));
    }

    // --- Estimated savings ---

    /**
     * AC7. Two segments priced differently, with self-consumption in both: the amount is each
     * tranche's own kWh at its own price, not the period's total at one of them. The two tranches
     * are deliberately unequal in both kWh and price, so a calculation that used the wrong price
     * for either one cannot land on the right total by accident.
     */
    @Test
    void eachSegmentIsPricedWithItsOwnTariff() {
        givenSupplyExists();
        givenAggregate(new DatadisConsumptionAggregate(0d, 30d, 0d, 48L));
        givenSegments(
                segment("2024-02-01", "2024-02-02", "0.10", TariffSource.REAL_TARIFF),
                segment("2024-02-02", "2024-02-03", "0.20", TariffSource.REAL_TARIFF));
        // 10 kWh on the first day, 20 kWh on the second.
        when(aggregateRepository.sumSelfConsumptionKWh(any(Supply.class),
                eq(madrid("2024-02-01T00:00:00+01:00")), eq(madrid("2024-02-02T00:00:00+01:00"))))
                .thenReturn(10d);
        when(aggregateRepository.sumSelfConsumptionKWh(any(Supply.class),
                eq(madrid("2024-02-02T00:00:00+01:00")), any()))
                .thenReturn(20d);

        SupplyEnergyMetrics metrics = service.getEnergyMetrics(supplyId,
                OffsetDateTime.parse("2024-02-01T00:00:00+01:00"),
                OffsetDateTime.parse("2024-02-02T23:00:00+01:00"));

        // 10 x 0.10 + 20 x 0.20 = 1.00 + 4.00 = 5.00. Pricing everything at either single rate
        // would give 3.00 or 6.00.
        assertEquals(0, new BigDecimal("5.00").compareTo(metrics.getSavings().getAmountEur()),
                () -> "Got " + metrics.getSavings().getAmountEur());
    }

    /**
     * Each segment is clamped to the period before being queried: the schedule may legitimately
     * start before and end after the period it was resolved for, and energy outside the period
     * must not be priced into it.
     */
    @Test
    void eachSegmentIsClampedToTheRealInstantsOfThePeriod() {
        givenSupplyExists();
        givenAggregate(new DatadisConsumptionAggregate(0d, 30d, 0d, 48L));
        givenSegments(
                segment("2020-01-01", "2024-02-02", "0.10", TariffSource.REAL_TARIFF),
                segment("2024-02-02", "2030-01-01", "0.20", TariffSource.REAL_TARIFF));

        service.getEnergyMetrics(supplyId,
                OffsetDateTime.parse("2024-02-01T10:00:00+01:00"),
                OffsetDateTime.parse("2024-02-02T15:00:00+01:00"));

        // Lower bound of the first segment is the period start, not 2020-01-01...
        verify(aggregateRepository).sumSelfConsumptionKWh(any(Supply.class),
                eq(madrid("2024-02-01T10:00:00+01:00")), eq(madrid("2024-02-02T00:00:00+01:00")));
        // ...and the upper bound of the second is the exclusive period end, not 2030-01-01.
        verify(aggregateRepository).sumSelfConsumptionKWh(any(Supply.class),
                eq(madrid("2024-02-02T00:00:00+01:00")),
                eq(madrid("2024-02-02T15:00:00+01:00").plusNanos(1)));
    }

    /**
     * A lone segment already covers the whole period, so re-querying the store for what the
     * aggregate just returned would be a second round trip for the same number. Reusing it also
     * makes the amount consistent with the reported selfConsumptionKWh to the last bit.
     */
    @Test
    void aSingleSegmentReusesTheAggregateInsteadOfIssuingAnotherQuery() {
        givenSupplyExists();
        givenAggregate(new DatadisConsumptionAggregate(0d, 53d, 0d, 3L));
        givenASingleEstimatedSegmentAt("0.15");

        SupplyEnergyMetrics metrics = service.getEnergyMetrics(supplyId, START_DATE, END_DATE);

        verify(aggregateRepository, never()).sumSelfConsumptionKWh(any(Supply.class), any(), any());
        // 53 x 0.15 = 7.95, over exactly the kWh the response reports as selfConsumptionKWh.
        assertEquals(0, new BigDecimal("7.95").compareTo(metrics.getSavings().getAmountEur()),
                () -> "Got " + metrics.getSavings().getAmountEur());
        assertEquals(53d, metrics.getSelfConsumptionKWh());
    }

    /**
     * VAT is carried by the segment rather than folded into the plan's price, so it has to be
     * applied on top of the taxable base.
     */
    @Test
    void vatIsAppliedOnTopOfTheEnergyTerm() {
        givenSupplyExists();
        givenAggregate(new DatadisConsumptionAggregate(0d, 100d, 0d, 3L));
        givenSegments(segment(new DateRange(LocalDate.parse("2024-02-01"), LocalDate.parse("2024-02-03")),
                "0.10", "0.21", TariffSource.REAL_TARIFF));

        SupplyEnergyMetrics metrics = service.getEnergyMetrics(supplyId, START_DATE, END_DATE);

        // 100 x 0.10 x 1.21 = 12.10, not 10.00.
        assertEquals(0, new BigDecimal("12.10").compareTo(metrics.getSavings().getAmountEur()),
                () -> "Got " + metrics.getSavings().getAmountEur());
    }

    /**
     * Zero self-consumed kWh over a resolved period is worth zero, which is a real answer. It is
     * not the same statement as "there was no period to price".
     */
    @Test
    void aResolvedPeriodWithoutSelfConsumptionIsWorthZero() {
        givenSupplyExists();
        givenAggregate(new DatadisConsumptionAggregate(10d, 0d, 0d, 3L));

        SupplyEnergyMetrics metrics = service.getEnergyMetrics(supplyId, START_DATE, END_DATE);

        assertEquals(0, BigDecimal.ZERO.compareTo(metrics.getSavings().getAmountEur()));
        assertEquals(TariffSource.ESTIMATE, metrics.getSavings().getTariffSource());
    }

    /**
     * With no stored record and no requested period there is nothing to price and no period to
     * price it over, so the amount is absent -- and no tariff is resolved at all.
     */
    @Test
    void anUnresolvablePeriodYieldsAnAbsentAmount() {
        givenSupplyExists();
        when(aggregateRepository.findRecordedPeriod(supply)).thenReturn(Optional.empty());

        SupplyEnergyMetrics metrics = service.getEnergyMetrics(supplyId, null, null);

        assertNull(metrics.getSavings().getAmountEur());
        assertEquals(TariffSource.ESTIMATE, metrics.getSavings().getTariffSource());
        verifyNoInteractions(tariffResolver);
    }

    @Test
    void aScheduleOfOnlyRealSegmentsReportsARealTariffSource() {
        givenSupplyExists();
        givenAggregate(new DatadisConsumptionAggregate(0d, 10d, 0d, 3L));
        givenSegments(
                segment("2024-02-01", "2024-02-02", "0.10", TariffSource.REAL_TARIFF),
                segment("2024-02-02", "2024-02-03", "0.20", TariffSource.REAL_TARIFF));

        SupplyEnergyMetrics metrics = service.getEnergyMetrics(supplyId, START_DATE, END_DATE);

        assertEquals(TariffSource.REAL_TARIFF, metrics.getSavings().getTariffSource());
    }

    /**
     * A total is only as trustworthy as its least trustworthy part: one estimated segment makes
     * the whole amount an estimate, whichever position it sits in.
     */
    @Test
    void oneEstimatedSegmentMakesTheWholeAmountAnEstimate() {
        givenSupplyExists();
        givenAggregate(new DatadisConsumptionAggregate(0d, 10d, 0d, 3L));
        givenSegments(
                segment("2024-02-01", "2024-02-02", "0.10", TariffSource.REAL_TARIFF),
                segment("2024-02-02", "2024-02-03", "0.20", TariffSource.ESTIMATE));

        SupplyEnergyMetrics metrics = service.getEnergyMetrics(supplyId, START_DATE, END_DATE);

        assertEquals(TariffSource.ESTIMATE, metrics.getSavings().getTariffSource());
    }

    @Test
    void anEstimatedFirstSegmentAlsoMakesTheWholeAmountAnEstimate() {
        givenSupplyExists();
        givenAggregate(new DatadisConsumptionAggregate(0d, 10d, 0d, 3L));
        givenSegments(
                segment("2024-02-01", "2024-02-02", "0.10", TariffSource.ESTIMATE),
                segment("2024-02-02", "2024-02-03", "0.20", TariffSource.REAL_TARIFF));

        SupplyEnergyMetrics metrics = service.getEnergyMetrics(supplyId, START_DATE, END_DATE);

        assertEquals(TariffSource.ESTIMATE, metrics.getSavings().getTariffSource());
    }

    /**
     * The resolver is asked for a civil range that contains the period whole. An inclusive end
     * maps to the day after the day containing it, so the period's last day is priced rather than
     * silently left out.
     */
    @Test
    void theResolverIsAskedForACivilRangeContainingTheWholePeriod() {
        givenSupplyExists();
        givenAggregate(new DatadisConsumptionAggregate(0d, 10d, 0d, 3L));

        service.getEnergyMetrics(supplyId,
                OffsetDateTime.parse("2024-02-01T10:00:00+01:00"),
                OffsetDateTime.parse("2024-02-03T23:00:00+01:00"));

        ArgumentCaptor<DateRange> range = ArgumentCaptor.forClass(DateRange.class);
        verify(tariffResolver).scheduleFor(eq(supplyId), range.capture());
        assertEquals(LocalDate.of(2024, 2, 1), range.getValue().getStart());
        assertEquals(LocalDate.of(2024, 2, 4), range.getValue().getEnd());
    }

    /**
     * The civil range is read in the configured zone, not in the offset the caller happened to
     * send: 23:30 UTC on 1 February is already 2 February in Madrid, and pricing it under the
     * first day's tariff would be wrong once the two days differ.
     */
    @Test
    void theCivilRangeIsReadInTheConfiguredZoneRatherThanTheCallersOffset() {
        givenSupplyExists();
        givenAggregate(new DatadisConsumptionAggregate(0d, 10d, 0d, 3L));

        service.getEnergyMetrics(supplyId,
                OffsetDateTime.parse("2024-02-01T23:30:00Z"),
                OffsetDateTime.parse("2024-02-02T23:30:00Z"));

        ArgumentCaptor<DateRange> range = ArgumentCaptor.forClass(DateRange.class);
        verify(tariffResolver).scheduleFor(eq(supplyId), range.capture());
        // 2024-02-01T23:30Z is 2024-02-02T00:30+01:00 in Madrid, and the end is 3 February there.
        assertEquals(LocalDate.of(2024, 2, 2), range.getValue().getStart());
        assertEquals(LocalDate.of(2024, 2, 4), range.getValue().getEnd());
    }

    /**
     * Only FlatPlan can be priced. A plan that cannot must fail loudly: skipping the segment
     * would price that stretch of the period at zero and report a figure wrong by exactly the
     * amount nobody can see.
     */
    @Test
    void aPlanThatCannotBePricedIsRejectedRatherThanSilentlySkipped() {
        givenSupplyExists();
        givenAggregate(new DatadisConsumptionAggregate(0d, 10d, 0d, 3L));
        when(tariffResolver.scheduleFor(eq(supplyId), any(DateRange.class))).thenReturn(
                new TariffSchedule(List.of(new TariffSegment(
                        new DateRange(LocalDate.parse("2024-02-01"), LocalDate.parse("2024-02-03")),
                        new TimeOfUsePlan(), BigDecimal.ZERO, TariffSource.REAL_TARIFF))));

        UnsupportedTariffPlanException exception = assertThrows(UnsupportedTariffPlanException.class,
                () -> service.getEnergyMetrics(supplyId, START_DATE, END_DATE));

        assertEquals(TimeOfUsePlan.class, exception.getPlanType());
    }

    private long expectedHoursBetween(String startDate, String endDate) {
        givenSupplyExists();
        givenAggregate(new DatadisConsumptionAggregate(1d, 0d, 0d, 1L));

        return service.getEnergyMetrics(supplyId,
                        OffsetDateTime.parse(startDate), OffsetDateTime.parse(endDate))
                .getExpectedHours();
    }

    private void givenSupplyExists() {
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));
    }

    private void givenConfiguredZoneIsMadrid() {
        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of("Europe/Madrid"));
    }

    private void givenAggregate(DatadisConsumptionAggregate aggregate) {
        when(aggregateRepository.aggregateByRangeOfDates(any(Supply.class), any(), any())).thenReturn(aggregate);
    }

    /**
     * What the only resolver shipping today produces: one estimated flat segment spanning exactly
     * the requested range.
     */
    private void givenASingleEstimatedSegmentAt(String pricePerKwh) {
        when(tariffResolver.scheduleFor(eq(supplyId), any(DateRange.class))).thenAnswer(invocation -> {
            DateRange requested = invocation.getArgument(1);
            return new TariffSchedule(List.of(segment(requested, pricePerKwh, "0", TariffSource.ESTIMATE)));
        });
    }

    private void givenSegments(TariffSegment... segments) {
        when(tariffResolver.scheduleFor(eq(supplyId), any(DateRange.class)))
                .thenReturn(new TariffSchedule(List.of(segments)));
    }

    private static TariffSegment segment(DateRange range, String pricePerKwh, String vatRate,
                                         TariffSource source) {
        return new TariffSegment(range, TariffPlan.flat(new BigDecimal(pricePerKwh)),
                new BigDecimal(vatRate), source);
    }

    private static TariffSegment segment(String startDate, String endDate, String pricePerKwh,
                                         TariffSource source) {
        return segment(new DateRange(LocalDate.parse(startDate), LocalDate.parse(endDate)),
                pricePerKwh, "0", source);
    }

    private static Instant madrid(String isoOffsetDateTime) {
        return OffsetDateTime.parse(isoOffsetDateTime).toInstant();
    }
}
