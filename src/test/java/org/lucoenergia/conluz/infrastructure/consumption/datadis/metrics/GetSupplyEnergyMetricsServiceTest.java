package org.lucoenergia.conluz.infrastructure.consumption.datadis.metrics;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DatadisConsumptionAggregate;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.consumption.GetSupplyEnergyMetricsService;
import org.lucoenergia.conluz.domain.consumption.InvalidEnergyMetricsPeriodException;
import org.lucoenergia.conluz.domain.consumption.RecordedConsumptionPeriod;
import org.lucoenergia.conluz.domain.consumption.SupplyEnergyMetrics;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.consumption.GetSupplyEnergyMetricsServiceImpl;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

    private final GetDatadisConsumptionAggregateRepository aggregateRepository =
            mock(GetDatadisConsumptionAggregateRepository.class);
    private final GetSupplyRepository getSupplyRepository = mock(GetSupplyRepository.class);
    private final TimeConfiguration timeConfiguration = mock(TimeConfiguration.class);
    private final GetSupplyEnergyMetricsService service = new GetSupplyEnergyMetricsServiceImpl(
            aggregateRepository, getSupplyRepository, new DateConverter(timeConfiguration));

    private final Supply supply = SupplyMother.random().build();
    private final SupplyId supplyId = SupplyId.of(supply.getId());

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
}
