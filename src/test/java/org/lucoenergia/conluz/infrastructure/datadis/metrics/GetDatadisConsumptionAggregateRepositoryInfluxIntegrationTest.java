package org.lucoenergia.conluz.infrastructure.datadis.metrics;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DailyPublishedAggregate;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DatadisConsumptionAggregate;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.consumption.RecordedConsumptionPeriod;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture.hourlyRecord;

/**
 * Integration tests for the aggregate repository against a real InfluxDB started by Testcontainers
 * (see {@link BaseIntegrationTest}).
 *
 * <p>These exercise the behaviour of the engine itself, which a mock could only assume: that a
 * query matching no point returns no series rather than a row of zeros, that a {@code SUM} over a
 * field the supply's records do not carry returns null rather than zero, that
 * {@code aggregateByRangeOfDates} and {@code sumSelfConsumptionKWh} both exclude their upper
 * bound, that a one-nanosecond difference between
 * two bounds survives into the query rather than being truncated away, and that
 * {@code FIRST}/{@code LAST} report the timestamps of real records.</p>
 *
 * <p>Each test owns its data under a unique CUPS, so nothing it writes to the shared measurement
 * can reach another test.</p>
 */
class GetDatadisConsumptionAggregateRepositoryInfluxIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GetDatadisConsumptionAggregateRepository repository;
    @Autowired
    private DatadisConsumptionInfluxFixture fixture;

    private static final Instant WIDE_FROM = Instant.parse("2023-01-01T00:00:00Z");
    private static final Instant WIDE_TO = Instant.parse("2025-01-01T00:00:00Z");

    private final List<String> createdCupsCodes = new ArrayList<>();

    @AfterEach
    void afterEach() {
        createdCupsCodes.forEach(fixture::clear);
    }

    @Test
    void aggregatesEveryFieldOverTheRangeAndCountsTheRecords() {
        Supply supply = supply();
        write(
                hourlyRecord(supply.getCode(), "2024/02/01", "01:00", 1.0f, 1.0f, 9.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "02:00", 100.0f, 50.0f, 0.5f),
                hourlyRecord(supply.getCode(), "2024/02/01", "03:00", 1.0f, 2.0f, 2.0f));

        DatadisConsumptionAggregate aggregate = repository.aggregateByRangeOfDates(supply,
                OffsetDateTime.parse("2024-02-01T00:00:00+01:00"),
                OffsetDateTime.parse("2024-02-01T03:00:00+01:00"));

        assertEquals(102d, aggregate.getConsumptionKWh(), 1e-6);
        assertEquals(53d, aggregate.getSelfConsumptionEnergyKWh(), 1e-6);
        assertEquals(11.5d, aggregate.getSurplusEnergyKWh(), 1e-6);
        assertEquals(3L, aggregate.getHoursWithData());
    }

    /**
     * The range is half-open: the record on the start bound counts, the one on the end bound does
     * not, and records outside the range contribute nothing.
     */
    @Test
    @DisplayName("EPR-001 AC1 the aggregate includes the record on startDate and excludes the one on endDate")
    void includesTheRecordOnTheStartBoundAndExcludesTheOneOnTheEndBound() {
        Supply supply = supply();
        write(
                hourlyRecord(supply.getCode(), "2024/02/01", "10:00", 16.0f, 0.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "11:00", 1.0f, 0.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "12:00", 2.0f, 0.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "13:00", 4.0f, 0.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "14:00", 32.0f, 0.0f, 0.0f));

        DatadisConsumptionAggregate aggregate = repository.aggregateByRangeOfDates(supply,
                OffsetDateTime.parse("2024-02-01T10:00:00+01:00"),
                OffsetDateTime.parse("2024-02-01T12:00:00+01:00"));

        // 1 + 2: the start bound is in; the end bound and the records either side are out.
        assertEquals(3d, aggregate.getConsumptionKWh(), 1e-6);
        assertEquals(2L, aggregate.getHoursWithData());
    }

    /**
     * InfluxDB answers a query that matches no point with no series at all, not with a row of
     * zeros. The repository has to turn that into an empty aggregate.
     */
    @Test
    void anEmptyResultSetBecomesAnAggregateOfZeros() {
        Supply supply = supply();
        write(hourlyRecord(supply.getCode(), "2024/02/01", "01:00", 5.0f, 1.0f, 1.0f));

        DatadisConsumptionAggregate aggregate = repository.aggregateByRangeOfDates(supply,
                OffsetDateTime.parse("2025-01-01T00:00:00+01:00"),
                OffsetDateTime.parse("2025-01-01T23:00:00+01:00"));

        assertEquals(0d, aggregate.getConsumptionKWh());
        assertEquals(0d, aggregate.getSelfConsumptionEnergyKWh());
        assertEquals(0d, aggregate.getSurplusEnergyKWh());
        assertEquals(0L, aggregate.getHoursWithData());
    }

    @Test
    void aSupplyThatHasNeverStoredARecordAggregatesToZeros() {
        Supply supply = supply();

        DatadisConsumptionAggregate aggregate = repository.aggregateByRangeOfDates(supply,
                OffsetDateTime.parse("2024-02-01T00:00:00+01:00"),
                OffsetDateTime.parse("2024-02-01T23:00:00+01:00"));

        assertEquals(0d, aggregate.getConsumptionKWh());
        assertEquals(0L, aggregate.getHoursWithData());
    }

    /**
     * A supply without self-consumption stores neither self-consumption nor surplus, while other
     * supplies in the same measurement do. Summing a field this supply's records do not carry
     * returns null, which must read as zero rather than blow up.
     */
    @Test
    void aNullSumOverAFieldTheRecordsDoNotCarryReadsAsZero() {
        Supply withSelfConsumption = supply();
        Supply withoutSelfConsumption = supply();

        // The first supply puts both fields in the measurement's schema...
        write(hourlyRecord(withSelfConsumption.getCode(), "2024/02/01", "01:00", 3.0f, 7.0f, 11.0f));
        // ...which the second one never writes, so its own sums come back null.
        write(
                hourlyRecord(withoutSelfConsumption.getCode(), "2024/02/01", "01:00", 5.0f, null, null),
                hourlyRecord(withoutSelfConsumption.getCode(), "2024/02/01", "02:00", 7.0f, null, null));

        DatadisConsumptionAggregate aggregate = repository.aggregateByRangeOfDates(withoutSelfConsumption,
                OffsetDateTime.parse("2024-02-01T00:00:00+01:00"),
                OffsetDateTime.parse("2024-02-01T02:00:00+01:00"));

        assertEquals(12d, aggregate.getConsumptionKWh(), 1e-6);
        assertEquals(0d, aggregate.getSelfConsumptionEnergyKWh());
        assertEquals(0d, aggregate.getSurplusEnergyKWh());
        assertEquals(2L, aggregate.getHoursWithData());
    }

    /**
     * Every query is scoped by the cups tag, so a busy measurement stays invisible to a supply.
     */
    @Test
    void anotherSupplysRecordsNeverReachTheAggregate() {
        Supply supply = supply();
        Supply neighbour = supply();

        write(hourlyRecord(supply.getCode(), "2024/02/01", "01:00", 5.0f, 1.0f, 1.0f));
        write(
                hourlyRecord(neighbour.getCode(), "2024/02/01", "01:00", 1000.0f, 1000.0f, 1000.0f),
                hourlyRecord(neighbour.getCode(), "2024/02/01", "02:00", 1000.0f, 1000.0f, 1000.0f));

        DatadisConsumptionAggregate aggregate = repository.aggregateByRangeOfDates(supply,
                OffsetDateTime.parse("2024-02-01T00:00:00+01:00"),
                OffsetDateTime.parse("2024-02-01T23:00:00+01:00"));

        assertEquals(5d, aggregate.getConsumptionKWh(), 1e-6);
        assertEquals(1L, aggregate.getHoursWithData());
    }

    @Test
    void findsTheInstantsOfTheEarliestAndLatestStoredRecords() {
        Supply supply = supply();
        write(
                hourlyRecord(supply.getCode(), "2023/04/10", "08:00", 1.0f, 0.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2023/06/15", "14:00", 2.0f, 0.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "24:00", 3.0f, 0.0f, 0.0f));

        Optional<RecordedConsumptionPeriod> period = repository.findRecordedPeriod(supply);

        assertTrue(period.isPresent());
        // 2023-04-10 07:00 in Europe/Madrid is +02:00, 2024-02-01 23:00 is +01:00.
        assertEquals(Instant.parse("2023-04-10T05:00:00Z"), period.get().getFirstRecord());
        assertEquals(Instant.parse("2024-02-01T22:00:00Z"), period.get().getLastRecord());
    }

    @Test
    void findsNoPeriodForASupplyWithoutAnyRecord() {
        assertTrue(repository.findRecordedPeriod(supply()).isEmpty());
    }

    @Test
    void findsThePeriodOfASingleRecord() {
        Supply supply = supply();
        write(hourlyRecord(supply.getCode(), "2024/02/01", "01:00", 1.0f, 0.0f, 0.0f));

        Optional<RecordedConsumptionPeriod> period = repository.findRecordedPeriod(supply);

        assertTrue(period.isPresent());
        assertEquals(period.get().getFirstRecord(), period.get().getLastRecord());
        assertEquals(Instant.parse("2024-01-31T23:00:00Z"), period.get().getFirstRecord());
    }

    /**
     * The period must come from this supply's own records, not from whatever the measurement
     * happens to span.
     */
    @Test
    void thePeriodIsScopedToTheSupply() {
        Supply supply = supply();
        Supply neighbour = supply();

        write(hourlyRecord(supply.getCode(), "2024/02/01", "01:00", 1.0f, 0.0f, 0.0f));
        write(
                hourlyRecord(neighbour.getCode(), "2020/01/01", "01:00", 1.0f, 0.0f, 0.0f),
                hourlyRecord(neighbour.getCode(), "2030/01/01", "01:00", 1.0f, 0.0f, 0.0f));

        Optional<RecordedConsumptionPeriod> period = repository.findRecordedPeriod(supply);

        assertTrue(period.isPresent());
        assertEquals(Instant.parse("2024-01-31T23:00:00Z"), period.get().getFirstRecord());
        assertEquals(Instant.parse("2024-01-31T23:00:00Z"), period.get().getLastRecord());
    }

    /**
     * The day the clocks go back repeats a local hour, so its 25 hourly instants span 25 elapsed
     * hours. Datadis labels that day with 25 records, {@code 03:00} twice, and each one is stored
     * at its own instant. Losing the offset here would move the reported period.
     */
    @Test
    void reportsThePeriodAcrossADaylightSavingTransitionInInstants() {
        Supply supply = supply();
        List<String> labels = new ArrayList<>(List.of("01:00", "02:00", "03:00", "03:00"));
        for (int hour = 4; hour <= 24; hour++) {
            labels.add(String.format("%02d:00", hour));
        }
        List<DatadisConsumption> records = new ArrayList<>();
        for (String label : labels) {
            records.add(hourlyRecord(supply.getCode(), "2023/10/29", label, 1.0f, 0.0f, 0.0f));
        }
        write(records.toArray(new DatadisConsumption[0]));

        Optional<RecordedConsumptionPeriod> period = repository.findRecordedPeriod(supply);

        assertTrue(period.isPresent());
        // The day starts at local 00:00, still +02:00; its last hour starts at local 23:00, already +01:00.
        assertEquals(Instant.parse("2023-10-28T22:00:00Z"), period.get().getFirstRecord());
        assertEquals(Instant.parse("2023-10-29T22:00:00Z"), period.get().getLastRecord());

        DatadisConsumptionAggregate aggregate = repository.aggregateByRangeOfDates(supply,
                OffsetDateTime.parse("2023-10-29T00:00:00+02:00"),
                OffsetDateTime.parse("2023-10-30T00:00:00+01:00"));
        assertEquals(25L, aggregate.getHoursWithData());

        // Both hours local 02:00 repeats, 00:00Z and 01:00Z, hold a record.
        DatadisConsumptionAggregate repeatedHour = repository.aggregateByRangeOfDates(supply,
                OffsetDateTime.parse("2023-10-29T02:00:00+02:00"),
                OffsetDateTime.parse("2023-10-29T03:00:00+01:00"));
        assertEquals(2L, repeatedHour.getHoursWithData());
    }

    // --- sumSelfConsumptionKWh: half-open [from, to) ---

    /**
     * The defining property of the half-open bound, and the reason it exists: the record on
     * {@code from} is in and the record on {@code to} is out, so two adjacent sub-intervals of one
     * period can be summed independently without the boundary hour landing in both.
     */
    @Test
    void sumsTheRecordOnTheLowerBoundAndExcludesTheOneOnTheUpperBound() {
        Supply supply = supply();
        write(
                hourlyRecord(supply.getCode(), "2024/02/01", "10:00", 0.0f, 16.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "11:00", 0.0f, 1.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "12:00", 0.0f, 2.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "13:00", 0.0f, 4.0f, 0.0f));

        double sum = repository.sumSelfConsumptionKWh(supply,
                OffsetDateTime.parse("2024-02-01T10:00:00+01:00").toInstant(),
                OffsetDateTime.parse("2024-02-01T12:00:00+01:00").toInstant());

        // 1 + 2: 10:00 is in, 12:00 is out, and 09:00 was never in range.
        assertEquals(3d, sum, 1e-6);
    }

    /**
     * Two adjacent half-open sub-intervals meeting at 11:00 sum to the whole, with the boundary
     * record counted exactly once. The same cut made with two inclusive-bound queries would
     * double-count it.
     */
    @Test
    void adjacentSubIntervalsSumToTheWholeWithoutDoubleCountingTheBoundary() {
        Supply supply = supply();
        write(
                hourlyRecord(supply.getCode(), "2024/02/01", "11:00", 0.0f, 1.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "12:00", 0.0f, 2.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "13:00", 0.0f, 4.0f, 0.0f));

        Instant from = OffsetDateTime.parse("2024-02-01T10:00:00+01:00").toInstant();
        Instant split = OffsetDateTime.parse("2024-02-01T11:00:00+01:00").toInstant();
        Instant to = DateConverter.toExclusiveUpperBound(OffsetDateTime.parse("2024-02-01T12:00:00+01:00"));

        double first = repository.sumSelfConsumptionKWh(supply, from, split);
        double second = repository.sumSelfConsumptionKWh(supply, split, to);

        assertEquals(1d, first, 1e-6);
        assertEquals(6d, second, 1e-6);
        assertEquals(7d, first + second, 1e-6);
    }

    /**
     * The nanosecond the exclusive bound is built from has to survive the trip to InfluxDB. If the
     * query literal were truncated to second precision the upper bound would collapse back onto
     * the inclusive end and silently drop the record sitting exactly on it -- the single most
     * damaging way this could fail, because every total would be short by one hour and still look
     * plausible.
     */
    @Test
    void theRecordOnThePublicInclusiveEndSurvivesTheNanosecondExclusiveBound() {
        Supply supply = supply();
        write(
                hourlyRecord(supply.getCode(), "2024/02/01", "11:00", 0.0f, 1.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "12:00", 0.0f, 2.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "13:00", 0.0f, 4.0f, 0.0f));

        OffsetDateTime inclusiveEnd = OffsetDateTime.parse("2024-02-01T11:00:00+01:00");

        double sum = repository.sumSelfConsumptionKWh(supply,
                OffsetDateTime.parse("2024-02-01T10:00:00+01:00").toInstant(),
                DateConverter.toExclusiveUpperBound(inclusiveEnd));

        // 1 + 2: the record on the inclusive end is in, the one an hour later is out.
        assertEquals(3d, sum, 1e-6);
    }

    /**
     * Over the whole period the half-open sum must agree exactly with the aggregate,
     * which is what lets a single-segment schedule reuse the aggregate instead of issuing a
     * second query.
     */
    @Test
    void theHalfOpenSumOverTheWholePeriodMatchesTheAggregate() {
        Supply supply = supply();
        write(
                hourlyRecord(supply.getCode(), "2024/02/01", "01:00", 1.0f, 1.0f, 9.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "02:00", 100.0f, 50.0f, 0.5f),
                hourlyRecord(supply.getCode(), "2024/02/01", "03:00", 1.0f, 2.0f, 2.0f));

        OffsetDateTime start = OffsetDateTime.parse("2024-02-01T00:00:00+01:00");
        OffsetDateTime end = OffsetDateTime.parse("2024-02-01T03:00:00+01:00");

        DatadisConsumptionAggregate aggregate = repository.aggregateByRangeOfDates(supply, start, end);
        double halfOpen = repository.sumSelfConsumptionKWh(supply, start.toInstant(), end.toInstant());

        assertEquals(53d, halfOpen, 1e-6);
        assertEquals(aggregate.getSelfConsumptionEnergyKWh(), halfOpen);
    }

    /**
     * A supply whose records never carry the field sums to null in InfluxDB, even though another
     * supply put the field in the measurement's schema. That has to read as zero.
     */
    @Test
    void aHalfOpenSumOverAFieldTheRecordsDoNotCarryReadsAsZero() {
        Supply withSelfConsumption = supply();
        Supply withoutSelfConsumption = supply();

        write(hourlyRecord(withSelfConsumption.getCode(), "2024/02/01", "01:00", 3.0f, 7.0f, 11.0f));
        write(
                hourlyRecord(withoutSelfConsumption.getCode(), "2024/02/01", "01:00", 5.0f, null, null),
                hourlyRecord(withoutSelfConsumption.getCode(), "2024/02/01", "02:00", 7.0f, null, null));

        double sum = repository.sumSelfConsumptionKWh(withoutSelfConsumption,
                OffsetDateTime.parse("2024-02-01T00:00:00+01:00").toInstant(),
                OffsetDateTime.parse("2024-02-01T02:00:00+01:00").toInstant());

        assertEquals(0d, sum);
    }

    /**
     * Every query is scoped by the cups tag, so a neighbour's much larger self-consumption stays
     * invisible even over the same instants.
     */
    @Test
    void anotherSupplysSelfConsumptionNeverReachesTheHalfOpenSum() {
        Supply supply = supply();
        Supply neighbour = supply();

        write(hourlyRecord(supply.getCode(), "2024/02/01", "01:00", 0.0f, 5.0f, 0.0f));
        write(
                hourlyRecord(neighbour.getCode(), "2024/02/01", "01:00", 0.0f, 1000.0f, 0.0f),
                hourlyRecord(neighbour.getCode(), "2024/02/01", "02:00", 0.0f, 1000.0f, 0.0f));

        double sum = repository.sumSelfConsumptionKWh(supply,
                OffsetDateTime.parse("2024-02-01T00:00:00+01:00").toInstant(),
                OffsetDateTime.parse("2024-02-02T00:00:00+01:00").toInstant());

        assertEquals(5d, sum, 1e-6);
    }

    @Test
    void anIntervalMatchingNoRecordSumsToZero() {
        Supply supply = supply();
        write(hourlyRecord(supply.getCode(), "2024/02/01", "01:00", 0.0f, 5.0f, 0.0f));

        double sum = repository.sumSelfConsumptionKWh(supply,
                OffsetDateTime.parse("2025-01-01T00:00:00+01:00").toInstant(),
                OffsetDateTime.parse("2025-01-02T00:00:00+01:00").toInstant());

        assertEquals(0d, sum);
    }

    @Test
    void aSupplyThatHasNeverStoredARecordSumsToZero() {
        double sum = repository.sumSelfConsumptionKWh(supply(),
                OffsetDateTime.parse("2024-02-01T00:00:00+01:00").toInstant(),
                OffsetDateTime.parse("2024-02-02T00:00:00+01:00").toInstant());

        assertEquals(0d, sum);
    }

    // --- findPublishedPeriod and countPublishedHours: records carrying self-consumption ---

    /**
     * A night hour of a published month carries a self-consumption of zero: it is published all
     * the same, and so is the period it bounds.
     */
    @Test
    void aRecordCarryingAZeroSelfConsumptionIsPublished() {
        Supply supply = supply();
        write(
                hourlyRecord(supply.getCode(), "2024/02/10", "03:00", 1.0f, 0.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/10", "13:00", 1.0f, 2.0f, 1.0f),
                hourlyRecord(supply.getCode(), "2024/02/10", "24:00", 1.0f, 0.0f, null));

        Optional<RecordedConsumptionPeriod> period = repository.findPublishedPeriod(supply, WIDE_FROM, WIDE_TO);

        assertTrue(period.isPresent());
        // 2024-02-10 in Europe/Madrid is +01:00.
        assertEquals(Instant.parse("2024-02-10T01:00:00Z"), period.get().getFirstRecord());
        assertEquals(Instant.parse("2024-02-10T22:00:00Z"), period.get().getLastRecord());
        assertEquals(3L, repository.countPublishedHours(supply, WIDE_FROM, WIDE_TO));
    }

    /**
     * Before a month is published Datadis already sends the surplus the meter measured, but no
     * self-consumption: a record carrying surplus alone, however large, is not published.
     */
    @Test
    void aRecordCarryingOnlySurplusIsNotPublished() {
        Supply supply = supply();
        write(
                hourlyRecord(supply.getCode(), "2024/02/10", "13:00", 1.0f, 2.0f, 1.0f),
                hourlyRecord(supply.getCode(), "2024/03/10", "13:00", 1.0f, null, 0.002f),
                hourlyRecord(supply.getCode(), "2024/03/11", "13:00", 1.0f, null, 40.0f),
                hourlyRecord(supply.getCode(), "2024/03/12", "13:00", 1.0f, null, null));

        Optional<RecordedConsumptionPeriod> period = repository.findPublishedPeriod(supply, WIDE_FROM, WIDE_TO);

        assertTrue(period.isPresent());
        assertEquals(Instant.parse("2024-02-10T11:00:00Z"), period.get().getLastRecord());
        assertEquals(1L, repository.countPublishedHours(supply, WIDE_FROM, WIDE_TO));
    }

    /**
     * The record on {@code from} is in and the record on {@code toExclusive} is out, so the months
     * of a window can be counted one by one without the boundary hour landing in two of them.
     */
    @Test
    void thePublishedRecordsAreReadOverAHalfOpenInterval() {
        Supply supply = supply();
        write(
                hourlyRecord(supply.getCode(), "2024/01/31", "24:00", 1.0f, 1.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "01:00", 1.0f, 1.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/29", "24:00", 1.0f, 1.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/03/01", "01:00", 1.0f, 1.0f, 0.0f));
        // February 2024 in Europe/Madrid.
        Instant from = Instant.parse("2024-01-31T23:00:00Z");
        Instant toExclusive = Instant.parse("2024-02-29T23:00:00Z");

        Optional<RecordedConsumptionPeriod> period = repository.findPublishedPeriod(supply, from, toExclusive);

        assertTrue(period.isPresent());
        assertEquals(from, period.get().getFirstRecord());
        assertEquals(Instant.parse("2024-02-29T22:00:00Z"), period.get().getLastRecord());
        assertEquals(2L, repository.countPublishedHours(supply, from, toExclusive));
    }

    @Test
    void anotherSupplysPublishedRecordsAreNeverReported() {
        Supply supply = supply();
        Supply neighbour = supply();
        write(hourlyRecord(supply.getCode(), "2024/02/10", "13:00", 1.0f, 2.0f, 1.0f));
        write(
                hourlyRecord(neighbour.getCode(), "2023/05/10", "13:00", 1.0f, 2.0f, 1.0f),
                hourlyRecord(neighbour.getCode(), "2024/05/10", "13:00", 1.0f, 2.0f, 1.0f));

        Optional<RecordedConsumptionPeriod> period = repository.findPublishedPeriod(supply, WIDE_FROM, WIDE_TO);

        assertTrue(period.isPresent());
        assertEquals(Instant.parse("2024-02-10T11:00:00Z"), period.get().getFirstRecord());
        assertEquals(Instant.parse("2024-02-10T11:00:00Z"), period.get().getLastRecord());
        assertEquals(1L, repository.countPublishedHours(supply, WIDE_FROM, WIDE_TO));
    }

    @Test
    void aSupplyWithoutAnyPublishedRecordHasNoPublishedPeriodAndNoPublishedHours() {
        Supply supply = supply();
        write(
                hourlyRecord(supply.getCode(), "2024/02/10", "13:00", 1.0f, null, 3.0f),
                hourlyRecord(supply.getCode(), "2024/02/10", "14:00", 1.0f, null, null));

        assertTrue(repository.findPublishedPeriod(supply, WIDE_FROM, WIDE_TO).isEmpty());
        assertEquals(0L, repository.countPublishedHours(supply, WIDE_FROM, WIDE_TO));
    }

    @Test
    void aSupplyThatHasNeverStoredARecordHasNoPublishedPeriodAndNoPublishedHours() {
        Supply supply = supply();

        assertTrue(repository.findPublishedPeriod(supply, WIDE_FROM, WIDE_TO).isEmpty());
        assertEquals(0L, repository.countPublishedHours(supply, WIDE_FROM, WIDE_TO));
    }

    /**
     * 25 October 2026 lasts 25 hours in Madrid; the day groups follow local midnight, so all of them
     * fall on that day and none on its neighbours.
     */
    @Test
    @DisplayName("CEM-006 the daily totals follow local days, the day the clocks go back included")
    void aggregateByLocalDayFollowsLocalDaysAcrossTheDaylightSavingChange() {
        Supply supply = supply();
        ZoneId madrid = ZoneId.of("Europe/Madrid");
        Instant dayStart = Instant.parse("2026-10-24T22:00:00Z");
        List<Instant> hours = new ArrayList<>();
        for (int hour = 0; hour < 25; hour++) {
            hours.add(dayStart.plusSeconds(3600L * hour));
        }
        fixture.writeAt(supply.getCode(), hours, 1f, 0.5f, 0.25f);
        // The next day: one record without self-consumption and one with it.
        fixture.writeAt(supply.getCode(), Instant.parse("2026-10-25T23:00:00Z"), 1f, null, 4f);
        fixture.writeAt(supply.getCode(), Instant.parse("2026-10-26T00:00:00Z"), 1f, 2f, 0f);

        List<DailyPublishedAggregate> days = repository.aggregateByLocalDay(supply,
                Instant.parse("2026-10-23T22:00:00Z"), Instant.parse("2026-10-26T23:00:00Z"), madrid);

        assertEquals(2, days.size(), "a day without records is absent: " + days);
        assertEquals(new DailyPublishedAggregate(LocalDate.of(2026, 10, 25), 25.0, 12.5, 6.25, 25, "Real"), days.get(0));
        assertEquals(LocalDate.of(2026, 10, 26), days.get(1).day());
        assertEquals(2.0, days.get(1).selfConsumptionKWh(), 1e-6);
        assertEquals(4.0, days.get(1).surplusKWh(), 1e-6);
        assertEquals(1L, days.get(1).publishedHours());
    }

    @Test
    @DisplayName("EPR-001 the daily totals include the record on the start bound and exclude the one on the end bound")
    void aggregateByLocalDayIsHalfOpen() {
        Supply supply = supply();
        ZoneId madrid = ZoneId.of("Europe/Madrid");
        Instant from = Instant.parse("2026-05-10T10:00:00Z");
        Instant to = Instant.parse("2026-05-10T12:00:00Z");
        fixture.writeAt(supply.getCode(), List.of(from.minusSeconds(3600), from, from.plusSeconds(3600), to),
                1f, 1f, 0f);

        List<DailyPublishedAggregate> days = repository.aggregateByLocalDay(supply, from, to, madrid);

        assertEquals(1, days.size());
        assertEquals(2L, days.get(0).publishedHours());
        assertEquals(2.0, days.get(0).selfConsumptionKWh(), 1e-6);
    }

    @Test
    @DisplayName("CEM-006 the daily totals of several supplies come back from one read, keyed by supply")
    void aggregateByLocalDayOfSeveralSuppliesIsKeyedBySupply() {
        Supply first = supply();
        Supply second = supply();
        Supply silent = supply();
        ZoneId madrid = ZoneId.of("Europe/Madrid");
        Instant may10 = Instant.parse("2026-05-09T22:00:00Z");
        fixture.writeAt(first.getCode(), List.of(may10, may10.plusSeconds(3600)), 1f, 1f, 0.5f);
        fixture.writeAt(second.getCode(), may10.plusSeconds(86_400), 1f, 3f, 0f);

        Map<String, List<DailyPublishedAggregate>> days = repository.aggregateByLocalDay(
                List.of(first, second, silent), may10, may10.plusSeconds(2 * 86_400), madrid);

        assertEquals(2, days.size(), "a supply without records is absent: " + days);
        assertEquals(List.of(new DailyPublishedAggregate(LocalDate.of(2026, 5, 10), 2.0, 2.0, 1.0, 2, "Real")),
                days.get(first.getCode()));
        assertEquals(List.of(new DailyPublishedAggregate(LocalDate.of(2026, 5, 11), 1.0, 3.0, 0.0, 1, "Real")),
                days.get(second.getCode()));
    }

    @Test
    void aggregateByLocalDayOfNoSupplyReadsNothing() {
        assertTrue(repository.aggregateByLocalDay(List.of(), Instant.parse("2026-05-09T22:00:00Z"),
                Instant.parse("2026-05-10T22:00:00Z"), ZoneId.of("Europe/Madrid")).isEmpty());
    }

    @Test
    @DisplayName("CEM-008 CEM-010 the surplus of several supplies is summed hour by hour, keyed by the start of the hour")
    void sumSurplusByHourSumsTheSuppliesHourByHour() {
        Supply first = supply();
        Supply second = supply();
        Supply outOfScope = supply();
        Instant ten = Instant.parse("2026-05-10T08:00:00Z");
        Instant eleven = ten.plusSeconds(3600);
        fixture.writeAt(first.getCode(), List.of(ten, eleven), 1f, 0f, 2f);
        fixture.writeAt(second.getCode(), ten, 1f, 0f, 3f);
        fixture.writeAt(outOfScope.getCode(), List.of(ten, eleven), 1f, 0f, 100f);

        Map<Instant, Double> surplus = repository.sumSurplusByHour(List.of(first, second), ten,
                ten.plusSeconds(3 * 3600), ZoneId.of("Europe/Madrid"));

        assertEquals(Map.of(ten, 5.0, eleven, 2.0), surplus);
    }

    @Test
    @DisplayName("EPR-001 CEM-008 the hourly surplus includes the record on the start bound and excludes the one on the end bound")
    void sumSurplusByHourIsHalfOpen() {
        Supply supply = supply();
        Instant from = Instant.parse("2026-05-10T10:00:00Z");
        Instant to = Instant.parse("2026-05-10T12:00:00Z");
        fixture.writeAt(supply.getCode(), List.of(from.minusSeconds(3600), from, from.plusSeconds(3600), to),
                1f, 0f, 1f);

        Map<Instant, Double> surplus = repository.sumSurplusByHour(List.of(supply), from, to,
                ZoneId.of("Europe/Madrid"));

        assertEquals(Map.of(from, 1.0, from.plusSeconds(3600), 1.0), surplus);
    }

    @Test
    @DisplayName("CEM-009 an hour whose records carry no surplus, or a surplus of zero, is absent")
    void sumSurplusByHourLeavesOutTheHoursWithoutSurplus() {
        Supply first = supply();
        Supply second = supply();
        Instant ten = Instant.parse("2026-05-10T08:00:00Z");
        fixture.writeAt(first.getCode(), ten, 1f, 1f, 0f);
        fixture.writeAt(second.getCode(), ten, 1f, 1f, 0f);
        fixture.writeAt(first.getCode(), ten.plusSeconds(3600), 1f, 1f, null);
        fixture.writeAt(second.getCode(), ten.plusSeconds(2 * 3600), 1f, 1f, 0.25f);

        Map<Instant, Double> surplus = repository.sumSurplusByHour(List.of(first, second), ten,
                ten.plusSeconds(3 * 3600), ZoneId.of("Europe/Madrid"));

        assertEquals(Map.of(ten.plusSeconds(2 * 3600), 0.25), surplus);
    }

    /**
     * 25 October 2026 lasts 25 hours in Madrid: each keeps its own key, the repeated 02:00 included.
     */
    @Test
    @DisplayName("CEM-008 the hourly surplus keeps every hour of the day the clocks go back")
    void sumSurplusByHourKeepsEveryHourAcrossTheDaylightSavingChange() {
        Supply supply = supply();
        Instant dayStart = Instant.parse("2026-10-24T22:00:00Z");
        List<Instant> hours = new ArrayList<>();
        for (int hour = 0; hour < 25; hour++) {
            hours.add(dayStart.plusSeconds(3600L * hour));
        }
        fixture.writeAt(supply.getCode(), hours, 1f, 0.5f, 0.25f);

        Map<Instant, Double> surplus = repository.sumSurplusByHour(List.of(supply), dayStart,
                dayStart.plusSeconds(25 * 3600), ZoneId.of("Europe/Madrid"));

        assertEquals(25, surplus.size());
        assertEquals(Set.copyOf(hours), surplus.keySet());
        assertTrue(surplus.values().stream().allMatch(kWh -> kWh == 0.25), () -> "was " + surplus);
    }

    @Test
    void sumSurplusByHourOfNoSupplyReadsNothing() {
        assertTrue(repository.sumSurplusByHour(List.of(), Instant.parse("2026-05-09T22:00:00Z"),
                Instant.parse("2026-05-10T22:00:00Z"), ZoneId.of("Europe/Madrid")).isEmpty());
    }

    private Supply supply() {
        String code = "ES" + RandomStringUtils.random(20, false, true);
        createdCupsCodes.add(code);
        return SupplyMother.random().withCode(code).build();
    }

    private void write(DatadisConsumption... records) {
        fixture.write(List.of(records));
    }
}
