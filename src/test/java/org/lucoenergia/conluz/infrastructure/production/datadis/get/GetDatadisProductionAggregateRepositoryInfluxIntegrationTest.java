package org.lucoenergia.conluz.infrastructure.production.datadis.get;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.production.datadis.get.DailyProductionAggregate;
import org.lucoenergia.conluz.domain.production.datadis.get.GetDatadisProductionAggregateRepository;
import org.lucoenergia.conluz.domain.production.datadis.get.ProductionAggregate;
import org.lucoenergia.conluz.domain.production.datadis.get.RecordedProductionPeriod;
import org.lucoenergia.conluz.infrastructure.production.datadis.DatadisProductionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Against a real InfluxDB: the half-open bounds, the per-code grouping, and the absence of an entry for
 * a code without records, which a mock could only assume. Each test owns its data under unique CUPS.
 */
class GetDatadisProductionAggregateRepositoryInfluxIntegrationTest extends BaseIntegrationTest {

    private static final Instant FROM = Instant.parse("2026-05-09T22:00:00Z");
    private static final Instant TO = Instant.parse("2026-05-10T22:00:00Z");
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    /** From 1 November 2024 to 1 March 2025 in Madrid. */
    private static final Instant NEW_YEAR_FROM = Instant.parse("2024-10-31T23:00:00Z");
    private static final Instant NEW_YEAR_TO = Instant.parse("2025-02-28T23:00:00Z");

    @Autowired
    private GetDatadisProductionAggregateRepository repository;
    @Autowired
    private DatadisProductionInfluxFixture fixture;

    private final List<String> writtenCups = new ArrayList<>();

    @AfterEach
    void clear() {
        writtenCups.forEach(fixture::clear);
    }

    @Test
    void sumsTheProductionOfEachCodeSeparately() {
        String first = cups();
        String second = cups();
        fixture.writeAt(first, FROM, 1.5f);
        fixture.writeAt(first, FROM.plusSeconds(3600), 2.5f);
        fixture.writeAt(second, FROM.plusSeconds(7200), 10f);

        Map<String, Double> sums = repository.sumProductionByCups(List.of(first, second), FROM, TO);

        assertEquals(2, sums.size());
        assertEquals(4.0, sums.get(first), 1e-6);
        assertEquals(10.0, sums.get(second), 1e-6);
    }

    @Test
    void countsTheRecordOnTheStartBoundAndNotTheOneOnTheEndBound() {
        String code = cups();
        fixture.writeAt(code, FROM, 1f);
        fixture.writeAt(code, TO, 100f);
        fixture.writeAt(code, FROM.minusSeconds(3600), 1000f);

        Map<String, Double> sums = repository.sumProductionByCups(List.of(code), FROM, TO);

        assertEquals(1.0, sums.get(code), 1e-6);
    }

    @Test
    void leavesOutACodeWithoutRecordsAndKeepsAMeasuredZero() {
        String silent = cups();
        String zero = cups();
        fixture.writeAt(zero, FROM, 0f);

        Map<String, Double> sums = repository.sumProductionByCups(List.of(silent, zero), FROM, TO);

        assertFalse(sums.containsKey(silent));
        assertEquals(0.0, sums.get(zero), 0.0);
    }

    @Test
    void readsNothingForNoCode() {
        assertTrue(repository.sumProductionByCups(List.of(), FROM, TO).isEmpty());
    }

    /**
     * 23:30 on 31 December 2024 in Madrid is 22:30 UTC, and 00:30 on 1 January 2025 is 23:30 UTC on 31
     * December: a split by UTC year would put both in 2024.
     */
    @Test
    @DisplayName("CEM-011 AC2 the production is split by the local calendar year, not the UTC one")
    void theProductionIsSplitByTheLocalYear() {
        String north = cups();
        String south = cups();
        fixture.writeAt(north, Instant.parse("2024-11-15T10:00:00Z"), 300f);
        fixture.writeAt(north, Instant.parse("2024-12-31T22:30:00Z"), 100f);
        fixture.writeAt(south, Instant.parse("2024-12-31T23:30:00Z"), 200f);
        fixture.writeAt(south, Instant.parse("2025-02-10T10:00:00Z"), 400f);

        Map<Integer, Double> sums = repository.sumProductionByYear(List.of(north, south), NEW_YEAR_FROM,
                NEW_YEAR_TO, MADRID);

        assertEquals(Map.of(2024, 400d, 2025, 600d), sums);
    }

    @Test
    @DisplayName("CEM-011 the production per year covers the given codes over the half-open bounds only")
    void theProductionPerYearCoversTheGivenCodesAndBoundsOnly() {
        String north = cups();
        String south = cups();
        String outside = cups();
        fixture.writeAt(north, NEW_YEAR_FROM, 1f);
        fixture.writeAt(south, NEW_YEAR_TO.minusSeconds(3600), 2f);
        fixture.writeAt(north, NEW_YEAR_FROM.minusSeconds(3600), 1000f);
        fixture.writeAt(south, NEW_YEAR_TO, 1000f);
        fixture.writeAt(outside, Instant.parse("2025-01-10T10:00:00Z"), 1000f);

        Map<Integer, Double> sums = repository.sumProductionByYear(List.of(north, south), NEW_YEAR_FROM,
                NEW_YEAR_TO, MADRID);

        assertEquals(Map.of(2024, 1d, 2025, 2d), sums);
    }

    @Test
    @DisplayName("CEM-011 CEM-013 a year without records is absent and a year of stored zeros maps to 0")
    void aYearWithoutRecordsIsAbsentAndAYearOfZerosIsZero() {
        String north = cups();
        String south = cups();
        fixture.writeAt(north, Instant.parse("2024-12-01T10:00:00Z"), 0f);
        fixture.writeAt(south, Instant.parse("2024-12-02T10:00:00Z"), 0f);

        Map<Integer, Double> sums = repository.sumProductionByYear(List.of(north, south),
                Instant.parse("2023-11-30T23:00:00Z"), NEW_YEAR_TO, MADRID);

        assertEquals(Map.of(2024, 0d), sums);
    }

    @Test
    void readsNothingPerYearForNoCode() {
        assertTrue(repository.sumProductionByYear(List.of(), NEW_YEAR_FROM, NEW_YEAR_TO, MADRID).isEmpty());
    }

    @Test
    @DisplayName("PEM-002 the totals of one code count its records, zeros included, over the half-open bounds")
    void theTotalsOfOneCodeCountItsRecordsZerosIncluded() {
        String plant = cups();
        String other = cups();
        fixture.writeAt(plant, FROM, 0f);
        fixture.writeAt(plant, FROM.plusSeconds(3600), 1.25f);
        fixture.writeAt(plant, FROM.plusSeconds(7200), 2.5f);
        fixture.writeAt(plant, FROM.minusSeconds(3600), 1000f);
        fixture.writeAt(plant, TO, 1000f);
        fixture.writeAt(other, FROM.plusSeconds(3600), 1000f);

        ProductionAggregate aggregate = repository.aggregate(plant, FROM, TO).orElseThrow();

        assertEquals(3.75, aggregate.productionKWh(), 1e-6);
        assertEquals(3, aggregate.hoursWithData());
    }

    @Test
    @DisplayName("PEM-002 an interval without records has no totals, and one of stored zeros totals 0")
    void anIntervalWithoutRecordsHasNoTotalsAndOneOfZerosTotalsZero() {
        String silent = cups();
        String zero = cups();
        fixture.writeAt(zero, FROM, 0f);
        fixture.writeAt(zero, FROM.plusSeconds(3600), 0f);

        assertEquals(Optional.empty(), repository.aggregate(silent, FROM, TO));
        assertEquals(Optional.of(new ProductionAggregate(0d, 2)), repository.aggregate(zero, FROM, TO));
    }

    @Test
    @DisplayName("PEM-003 the records of one code are totalled and counted per local day, days without records left out")
    void theRecordsAreTotalledAndCountedPerLocalDay() {
        String plant = cups();
        String other = cups();
        // 2026-05-10T00:00 and 23:00 in Madrid belong to the 10th; 2026-05-12T01:00 to the 12th.
        fixture.writeAt(plant, Instant.parse("2026-05-09T22:00:00Z"), 1f);
        fixture.writeAt(plant, Instant.parse("2026-05-10T21:00:00Z"), 0f);
        fixture.writeAt(plant, Instant.parse("2026-05-11T23:00:00Z"), 4f);
        fixture.writeAt(other, Instant.parse("2026-05-10T10:00:00Z"), 1000f);

        List<DailyProductionAggregate> days = repository.aggregateByLocalDay(plant,
                Instant.parse("2026-05-09T22:00:00Z"), Instant.parse("2026-05-12T22:00:00Z"), MADRID);

        assertEquals(List.of(
                new DailyProductionAggregate(LocalDate.of(2026, 5, 10), 1d, 2),
                new DailyProductionAggregate(LocalDate.of(2026, 5, 12), 4d, 1)), days);
    }

    @Test
    @DisplayName("PEM-003 AC9 the local day the clocks go back groups its 25 hours")
    void theDayTheClocksGoBackGroupsItsTwentyFiveHours() {
        String plant = cups();
        Instant dayStart = Instant.parse("2026-10-24T22:00:00Z");
        for (int hour = 0; hour < 25; hour++) {
            fixture.writeAt(plant, dayStart.plusSeconds(3600L * hour), hour % 2 == 0 ? 0f : 1f);
        }
        fixture.writeAt(plant, dayStart.plusSeconds(3600L * 25), 7f);

        List<DailyProductionAggregate> days = repository.aggregateByLocalDay(plant, dayStart,
                Instant.parse("2026-10-26T23:00:00Z"), MADRID);

        assertEquals(List.of(
                new DailyProductionAggregate(LocalDate.of(2026, 10, 25), 12d, 25),
                new DailyProductionAggregate(LocalDate.of(2026, 10, 26), 7d, 1)), days);
    }

    @Test
    @DisplayName("PEM-003 AC9 the local day the clocks go forward groups its 23 hours")
    void theDayTheClocksGoForwardGroupsItsTwentyThreeHours() {
        String plant = cups();
        Instant dayStart = Instant.parse("2026-03-28T23:00:00Z");
        for (int hour = 0; hour < 23; hour++) {
            fixture.writeAt(plant, dayStart.plusSeconds(3600L * hour), 1f);
        }
        fixture.writeAt(plant, dayStart.plusSeconds(3600L * 23), 5f);

        List<DailyProductionAggregate> days = repository.aggregateByLocalDay(plant, dayStart,
                Instant.parse("2026-03-30T22:00:00Z"), MADRID);

        assertEquals(List.of(
                new DailyProductionAggregate(LocalDate.of(2026, 3, 29), 23d, 23),
                new DailyProductionAggregate(LocalDate.of(2026, 3, 30), 5d, 1)), days);
    }

    @Test
    void theRecordedPeriodRunsFromTheFirstToTheLastRecordOfTheCode() {
        String plant = cups();
        String other = cups();
        fixture.writeAt(plant, Instant.parse("2026-05-10T10:00:00Z"), 1f);
        fixture.writeAt(plant, Instant.parse("2026-03-01T08:00:00Z"), 0f);
        fixture.writeAt(plant, Instant.parse("2026-07-20T19:00:00Z"), 0f);
        fixture.writeAt(other, Instant.parse("2025-01-01T00:00:00Z"), 1f);

        assertEquals(Optional.of(new RecordedProductionPeriod(Instant.parse("2026-03-01T08:00:00Z"),
                        Instant.parse("2026-07-20T19:00:00Z"))),
                repository.findRecordedPeriod(plant));
    }

    @Test
    void aCodeWithoutRecordsHasNoRecordedPeriod() {
        assertEquals(Optional.empty(), repository.findRecordedPeriod(cups()));
    }

    @Test
    @DisplayName("PEM-003 AC7 the records of one code are read hour by hour, stored zeros kept and gaps absent")
    void theRecordsAreReadHourByHour() {
        String plant = cups();
        String other = cups();
        fixture.writeAt(plant, FROM, 0f);
        fixture.writeAt(plant, FROM.plusSeconds(7200), 3.5f);
        fixture.writeAt(plant, TO, 1000f);
        fixture.writeAt(other, FROM.plusSeconds(3600), 1000f);

        assertEquals(Map.of(FROM, 0d, FROM.plusSeconds(7200), 3.5d),
                repository.findHourlyProduction(plant, FROM, TO));
    }

    private String cups() {
        String code = SupplyMother.randomCups();
        writtenCups.add(code);
        return code;
    }
}
