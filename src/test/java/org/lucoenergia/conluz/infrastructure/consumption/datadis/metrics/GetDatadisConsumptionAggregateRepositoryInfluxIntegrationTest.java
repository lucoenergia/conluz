package org.lucoenergia.conluz.infrastructure.consumption.datadis.metrics;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DatadisConsumptionAggregate;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.consumption.RecordedConsumptionPeriod;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConsumptionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConsumptionInfluxFixture.hourlyRecord;

/**
 * Integration tests for the aggregate repository against a real InfluxDB started by Testcontainers
 * (see {@link BaseIntegrationTest}).
 *
 * <p>These exercise the behaviour of the engine itself, which a mock could only assume: that a
 * query matching no point returns no series rather than a row of zeros, that a {@code SUM} over a
 * field the supply's records do not carry returns null rather than zero, that
 * {@code aggregateByRangeOfDates}' two bounds are inclusive while
 * {@code sumSelfConsumptionKWh}'s upper bound is not, that a one-nanosecond difference between
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

    private final List<String> createdCupsCodes = new ArrayList<>();

    @AfterEach
    void afterEach() {
        createdCupsCodes.forEach(fixture::clear);
    }

    @Test
    void aggregatesEveryFieldOverTheRangeAndCountsTheRecords() {
        Supply supply = supply();
        write(
                hourlyRecord(supply.getCode(), "2024/02/01", "00:00", 1.0f, 1.0f, 9.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "01:00", 100.0f, 50.0f, 0.5f),
                hourlyRecord(supply.getCode(), "2024/02/01", "02:00", 1.0f, 2.0f, 2.0f));

        DatadisConsumptionAggregate aggregate = repository.aggregateByRangeOfDates(supply,
                OffsetDateTime.parse("2024-02-01T00:00:00+01:00"),
                OffsetDateTime.parse("2024-02-01T02:00:00+01:00"));

        assertEquals(102d, aggregate.getConsumptionKWh(), 1e-6);
        assertEquals(53d, aggregate.getSelfConsumptionEnergyKWh(), 1e-6);
        assertEquals(11.5d, aggregate.getSurplusEnergyKWh(), 1e-6);
        assertEquals(3L, aggregate.getHoursWithData());
    }

    /**
     * Both range bounds are inclusive, and records outside the range contribute nothing.
     */
    @Test
    void includesTheRecordsSittingExactlyOnEitherBoundAndExcludesTheRest() {
        Supply supply = supply();
        write(
                hourlyRecord(supply.getCode(), "2024/02/01", "09:00", 16.0f, 0.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "10:00", 1.0f, 0.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "11:00", 2.0f, 0.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "12:00", 4.0f, 0.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "13:00", 32.0f, 0.0f, 0.0f));

        DatadisConsumptionAggregate aggregate = repository.aggregateByRangeOfDates(supply,
                OffsetDateTime.parse("2024-02-01T10:00:00+01:00"),
                OffsetDateTime.parse("2024-02-01T12:00:00+01:00"));

        // 1 + 2 + 4: the two bounds are in, the records either side of them are out.
        assertEquals(7d, aggregate.getConsumptionKWh(), 1e-6);
        assertEquals(3L, aggregate.getHoursWithData());
    }

    /**
     * InfluxDB answers a query that matches no point with no series at all, not with a row of
     * zeros. The repository has to turn that into an empty aggregate.
     */
    @Test
    void anEmptyResultSetBecomesAnAggregateOfZeros() {
        Supply supply = supply();
        write(hourlyRecord(supply.getCode(), "2024/02/01", "00:00", 5.0f, 1.0f, 1.0f));

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
        write(hourlyRecord(withSelfConsumption.getCode(), "2024/02/01", "00:00", 3.0f, 7.0f, 11.0f));
        // ...which the second one never writes, so its own sums come back null.
        write(
                hourlyRecord(withoutSelfConsumption.getCode(), "2024/02/01", "00:00", 5.0f, null, null),
                hourlyRecord(withoutSelfConsumption.getCode(), "2024/02/01", "01:00", 7.0f, null, null));

        DatadisConsumptionAggregate aggregate = repository.aggregateByRangeOfDates(withoutSelfConsumption,
                OffsetDateTime.parse("2024-02-01T00:00:00+01:00"),
                OffsetDateTime.parse("2024-02-01T01:00:00+01:00"));

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

        write(hourlyRecord(supply.getCode(), "2024/02/01", "00:00", 5.0f, 1.0f, 1.0f));
        write(
                hourlyRecord(neighbour.getCode(), "2024/02/01", "00:00", 1000.0f, 1000.0f, 1000.0f),
                hourlyRecord(neighbour.getCode(), "2024/02/01", "01:00", 1000.0f, 1000.0f, 1000.0f));

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
                hourlyRecord(supply.getCode(), "2023/04/10", "07:00", 1.0f, 0.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2023/06/15", "13:00", 2.0f, 0.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "23:00", 3.0f, 0.0f, 0.0f));

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
        write(hourlyRecord(supply.getCode(), "2024/02/01", "00:00", 1.0f, 0.0f, 0.0f));

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

        write(hourlyRecord(supply.getCode(), "2024/02/01", "00:00", 1.0f, 0.0f, 0.0f));
        write(
                hourlyRecord(neighbour.getCode(), "2020/01/01", "00:00", 1.0f, 0.0f, 0.0f),
                hourlyRecord(neighbour.getCode(), "2030/01/01", "00:00", 1.0f, 0.0f, 0.0f));

        Optional<RecordedConsumptionPeriod> period = repository.findRecordedPeriod(supply);

        assertTrue(period.isPresent());
        assertEquals(Instant.parse("2024-01-31T23:00:00Z"), period.get().getFirstRecord());
        assertEquals(Instant.parse("2024-01-31T23:00:00Z"), period.get().getLastRecord());
    }

    /**
     * The day the clocks go back repeats a local hour, so its 24 distinct hourly instants span 24
     * elapsed hours rather than 23. Losing the offset here would move the reported period.
     */
    @Test
    void reportsThePeriodAcrossADaylightSavingTransitionInInstants() {
        Supply supply = supply();
        List<DatadisConsumption> records = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            records.add(hourlyRecord(supply.getCode(), "2023/10/29",
                    String.format("%02d:00", hour), 1.0f, 0.0f, 0.0f));
        }
        write(records.toArray(new DatadisConsumption[0]));

        Optional<RecordedConsumptionPeriod> period = repository.findRecordedPeriod(supply);

        assertTrue(period.isPresent());
        // Local 00:00 is still +02:00; local 23:00 is already +01:00.
        assertEquals(Instant.parse("2023-10-28T22:00:00Z"), period.get().getFirstRecord());
        assertEquals(Instant.parse("2023-10-29T22:00:00Z"), period.get().getLastRecord());

        DatadisConsumptionAggregate aggregate = repository.aggregateByRangeOfDates(supply,
                OffsetDateTime.parse("2023-10-29T00:00:00+02:00"),
                OffsetDateTime.parse("2023-10-29T23:00:00+01:00"));
        assertEquals(24L, aggregate.getHoursWithData());
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
                hourlyRecord(supply.getCode(), "2024/02/01", "09:00", 0.0f, 16.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "10:00", 0.0f, 1.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "11:00", 0.0f, 2.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "12:00", 0.0f, 4.0f, 0.0f));

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
                hourlyRecord(supply.getCode(), "2024/02/01", "10:00", 0.0f, 1.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "11:00", 0.0f, 2.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "12:00", 0.0f, 4.0f, 0.0f));

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
                hourlyRecord(supply.getCode(), "2024/02/01", "10:00", 0.0f, 1.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "11:00", 0.0f, 2.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "12:00", 0.0f, 4.0f, 0.0f));

        OffsetDateTime inclusiveEnd = OffsetDateTime.parse("2024-02-01T11:00:00+01:00");

        double sum = repository.sumSelfConsumptionKWh(supply,
                OffsetDateTime.parse("2024-02-01T10:00:00+01:00").toInstant(),
                DateConverter.toExclusiveUpperBound(inclusiveEnd));

        // 1 + 2: the record on the inclusive end is in, the one an hour later is out.
        assertEquals(3d, sum, 1e-6);
    }

    /**
     * Over the whole period the half-open sum must agree exactly with the inclusive aggregate,
     * which is what lets a single-segment schedule reuse the aggregate instead of issuing a
     * second query.
     */
    @Test
    void theHalfOpenSumOverTheWholePeriodMatchesTheInclusiveAggregate() {
        Supply supply = supply();
        write(
                hourlyRecord(supply.getCode(), "2024/02/01", "00:00", 1.0f, 1.0f, 9.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "01:00", 100.0f, 50.0f, 0.5f),
                hourlyRecord(supply.getCode(), "2024/02/01", "02:00", 1.0f, 2.0f, 2.0f));

        OffsetDateTime start = OffsetDateTime.parse("2024-02-01T00:00:00+01:00");
        OffsetDateTime end = OffsetDateTime.parse("2024-02-01T02:00:00+01:00");

        DatadisConsumptionAggregate aggregate = repository.aggregateByRangeOfDates(supply, start, end);
        double halfOpen = repository.sumSelfConsumptionKWh(supply,
                start.toInstant(), DateConverter.toExclusiveUpperBound(end));

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

        write(hourlyRecord(withSelfConsumption.getCode(), "2024/02/01", "00:00", 3.0f, 7.0f, 11.0f));
        write(
                hourlyRecord(withoutSelfConsumption.getCode(), "2024/02/01", "00:00", 5.0f, null, null),
                hourlyRecord(withoutSelfConsumption.getCode(), "2024/02/01", "01:00", 7.0f, null, null));

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

        write(hourlyRecord(supply.getCode(), "2024/02/01", "00:00", 0.0f, 5.0f, 0.0f));
        write(
                hourlyRecord(neighbour.getCode(), "2024/02/01", "00:00", 0.0f, 1000.0f, 0.0f),
                hourlyRecord(neighbour.getCode(), "2024/02/01", "01:00", 0.0f, 1000.0f, 0.0f));

        double sum = repository.sumSelfConsumptionKWh(supply,
                OffsetDateTime.parse("2024-02-01T00:00:00+01:00").toInstant(),
                OffsetDateTime.parse("2024-02-02T00:00:00+01:00").toInstant());

        assertEquals(5d, sum, 1e-6);
    }

    @Test
    void anIntervalMatchingNoRecordSumsToZero() {
        Supply supply = supply();
        write(hourlyRecord(supply.getCode(), "2024/02/01", "00:00", 0.0f, 5.0f, 0.0f));

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

    private Supply supply() {
        String code = "ES" + RandomStringUtils.random(20, false, true);
        createdCupsCodes.add(code);
        return SupplyMother.random().withCode(code).build();
    }

    private void write(DatadisConsumption... records) {
        fixture.write(List.of(records));
    }
}
