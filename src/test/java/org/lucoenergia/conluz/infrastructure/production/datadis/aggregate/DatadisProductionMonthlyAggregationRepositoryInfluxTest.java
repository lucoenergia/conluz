package org.lucoenergia.conluz.infrastructure.production.datadis.aggregate;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.datadis.aggregate.DatadisProductionMonthlyAggregationRepository;
import org.lucoenergia.conluz.infrastructure.production.datadis.DatadisProductionMeasurements;
import org.lucoenergia.conluz.infrastructure.production.datadis.DatadisProductionMonthlyPoint;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.Month;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for {@link DatadisProductionMonthlyAggregationRepositoryInflux}.
 * <p>
 * Seeds hourly {@code datadis_production_kwh} points for two supplies (CUPS) across two months
 * (March and April 2023) and asserts that aggregating April produces the correct per-cups sums,
 * that the aggregated point lands at the first day of the month at local midnight, and that
 * re-running the aggregation overwrites the point rather than duplicating it.
 * <p>
 * The aggregated record timestamp is the first day of April 2023 at midnight local time
 * (Europe/Madrid), which is 2023-03-31T22:00:00Z in UTC.
 */
@SpringBootTest
class DatadisProductionMonthlyAggregationRepositoryInfluxTest extends BaseIntegrationTest {

    private static final String CUPS_A = "ES0031406912345678JN0F";
    private static final String CUPS_B = "ES0031406987654321AB1C";

    // April 2023 window wide enough to capture April 1 local midnight in any timezone.
    private static final String APRIL_WINDOW_START = "2023-03-31T20:00:00Z";
    private static final String APRIL_WINDOW_END = "2023-04-01T04:00:00Z";

    @Autowired
    private DatadisProductionMonthlyAggregationRepository repository;

    @Autowired
    private InfluxDbConnectionManager influxDbConnectionManager;

    private Supply supplyA;
    private Supply supplyB;

    @BeforeEach
    void setUp() {
        User user = UserMother.randomUser();
        supplyA = SupplyMother.random(user).withCode(CUPS_A).build();
        supplyB = SupplyMother.random(user).withCode(CUPS_B).build();
        loadHourlyData();
    }

    @AfterEach
    void tearDown() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            for (String measurement : List.of(
                    DatadisProductionMeasurements.PRODUCTION_KWH_MEASUREMENT,
                    DatadisProductionMeasurements.PRODUCTION_KWH_MONTH_MEASUREMENT)) {
                for (String cups : List.of(CUPS_A, CUPS_B)) {
                    connection.query(new Query(String.format(
                            "DROP SERIES FROM \"%s\" WHERE \"cups\" = '%s'",
                            measurement, cups)));
                }
            }
        }
    }

    @Test
    void testAggregateMonthlyProductionComputesCorrectPerCupsSums() {

        // When - aggregate April 2023 for both supplies
        repository.aggregateMonthlyProduction(supplyA, Month.APRIL, 2023);
        repository.aggregateMonthlyProduction(supplyB, Month.APRIL, 2023);

        // Then - each cups has exactly one monthly point holding its own April sum
        DatadisProductionMonthlyPoint pointA = singleMonthlyPoint(CUPS_A, APRIL_WINDOW_START, APRIL_WINDOW_END);
        assertEquals(CUPS_A, pointA.getCups());
        assertNotNull(pointA.getProductionKWh());
        // 0.5 + 1.0 + 1.5 = 3.0 (April only; the March point must not be included)
        assertEquals(3.0, pointA.getProductionKWh(), 0.001,
                "CUPS_A April production must sum only its April hourly points");
        assertEquals("Real", pointA.getObtainMethod());

        DatadisProductionMonthlyPoint pointB = singleMonthlyPoint(CUPS_B, APRIL_WINDOW_START, APRIL_WINDOW_END);
        assertEquals(CUPS_B, pointB.getCups());
        assertNotNull(pointB.getProductionKWh());
        // 0.2 + 0.3 = 0.5
        assertEquals(0.5, pointB.getProductionKWh(), 0.001,
                "CUPS_B April production must sum only its April hourly points");
    }

    @Test
    void testAggregateMonthlyProductionSetsTimestampToFirstDayOfMonth() {

        // When
        repository.aggregateMonthlyProduction(supplyA, Month.APRIL, 2023);

        // Then - point exists in the April 1 local-midnight window...
        List<DatadisProductionMonthlyPoint> inWindow = queryMonthlyData(CUPS_A, APRIL_WINDOW_START, APRIL_WINDOW_END);
        assertFalse(inWindow.isEmpty(),
                "Aggregated point should be stored at the first day of the month at midnight (local time)");

        // ...and NOT in the previous month's window
        List<DatadisProductionMonthlyPoint> previousMonth = queryMonthlyData(
                CUPS_A, "2023-03-01T00:00:00Z", "2023-03-31T19:59:59Z");
        assertTrue(previousMonth.isEmpty(),
                "Aggregated point must not appear in the previous month's time window");
    }

    @Test
    void testReRunningAggregationOverwritesInsteadOfDuplicating() {

        // When - aggregate the same month twice
        repository.aggregateMonthlyProduction(supplyA, Month.APRIL, 2023);
        repository.aggregateMonthlyProduction(supplyA, Month.APRIL, 2023);

        // Then - still exactly one point (deterministic timestamp overwrites) with the same value
        List<DatadisProductionMonthlyPoint> result = queryMonthlyData(CUPS_A, APRIL_WINDOW_START, APRIL_WINDOW_END);
        assertEquals(1, result.size(), "Re-running the aggregation must overwrite, not duplicate");
        assertEquals(3.0, result.get(0).getProductionKWh(), 0.001);
    }

    @Test
    void testAggregateMonthlyProductionWithNoHourlyDataDoesNotWrite() {

        // When - aggregate January 2023, which has no hourly data loaded
        repository.aggregateMonthlyProduction(supplyA, Month.JANUARY, 2023);

        // Then - nothing is written to the monthly measurement for January 2023
        List<DatadisProductionMonthlyPoint> result = queryMonthlyData(
                CUPS_A, "2022-12-31T20:00:00Z", "2023-01-01T04:00:00Z");
        assertTrue(result.isEmpty(),
                "No monthly data should be written when there is no hourly source data");
    }

    @Test
    @DisplayName("DCA-002 the monthly production point sums the hourly records of the local month, in winter and summer time")
    void testAggregateMonthlyProductionSumsTheHourlyRecordsOfTheLocalMonth() {
        // Europe/Madrid. January 2026 (CET, UTC+1) is [2025-12-31T23:00Z, 2026-01-31T23:00Z);
        // April 2026 (CEST, UTC+2) is [2026-03-31T22:00Z, 2026-04-30T22:00Z).
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();
            loadHourlyPoint(batchPoints, CUPS_A, "2025-12-31T22:00:00Z", 1000.0); // Dec 31 23:00 local
            loadHourlyPoint(batchPoints, CUPS_A, "2025-12-31T23:00:00Z", 1.0);    // Jan 1 00:00 local
            loadHourlyPoint(batchPoints, CUPS_A, "2026-01-15T12:00:00Z", 10.0);
            loadHourlyPoint(batchPoints, CUPS_A, "2026-01-31T22:00:00Z", 100.0);  // Jan 31 23:00 local
            loadHourlyPoint(batchPoints, CUPS_A, "2026-01-31T23:00:00Z", 2000.0); // Feb 1 00:00 local
            loadHourlyPoint(batchPoints, CUPS_A, "2026-03-31T21:00:00Z", 3000.0); // Mar 31 23:00 local
            loadHourlyPoint(batchPoints, CUPS_A, "2026-03-31T22:00:00Z", 2.0);    // Apr 1 00:00 local
            loadHourlyPoint(batchPoints, CUPS_A, "2026-04-30T21:00:00Z", 20.0);   // Apr 30 23:00 local
            loadHourlyPoint(batchPoints, CUPS_A, "2026-04-30T22:00:00Z", 4000.0); // May 1 00:00 local
            connection.write(batchPoints);
        }

        repository.aggregateMonthlyProduction(supplyA, Month.JANUARY, 2026);
        repository.aggregateMonthlyProduction(supplyA, Month.APRIL, 2026);

        DatadisProductionMonthlyPoint january = singleMonthlyPoint(CUPS_A,
                "2025-12-31T23:00:00Z", "2025-12-31T23:00:00Z");
        assertEquals(111.0, january.getProductionKWh(), 0.001,
                "January must sum exactly the hours of the local month");
        DatadisProductionMonthlyPoint april = singleMonthlyPoint(CUPS_A,
                "2026-03-31T22:00:00Z", "2026-03-31T22:00:00Z");
        assertEquals(22.0, april.getProductionKWh(), 0.001,
                "April must sum exactly the hours of the local month");
    }

    // -----------------------------------------------------------------------
    // Data setup helpers
    // -----------------------------------------------------------------------

    private void loadHourlyData() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();

            // CUPS_A - April 1, 2023: 0.5 + 1.0 + 1.5 = 3.0
            loadHourlyPoint(batchPoints, CUPS_A, 1680307200000000000L, 0.5); // Apr 1 00:00Z
            loadHourlyPoint(batchPoints, CUPS_A, 1680310800000000000L, 1.0); // Apr 1 01:00Z
            loadHourlyPoint(batchPoints, CUPS_A, 1680314400000000000L, 1.5); // Apr 1 02:00Z

            // CUPS_A - March 15, 2023: 2.0 (must NOT be included in the April aggregate)
            loadHourlyPoint(batchPoints, CUPS_A, 1678838400000000000L, 2.0); // Mar 15 12:00Z

            // CUPS_B - April 1, 2023: 0.2 + 0.3 = 0.5
            loadHourlyPoint(batchPoints, CUPS_B, 1680307200000000000L, 0.2); // Apr 1 00:00Z
            loadHourlyPoint(batchPoints, CUPS_B, 1680310800000000000L, 0.3); // Apr 1 01:00Z

            connection.write(batchPoints);
        }
    }

    private void loadHourlyPoint(BatchPoints batchPoints, String cups, String instant, double production) {
        loadHourlyPoint(batchPoints, cups, Instant.parse(instant).toEpochMilli() * 1_000_000L, production);
    }

    private void loadHourlyPoint(BatchPoints batchPoints, String cups, long timestampNanos, double production) {
        batchPoints.point(Point.measurement(DatadisProductionMeasurements.PRODUCTION_KWH_MEASUREMENT)
                .time(timestampNanos, TimeUnit.NANOSECONDS)
                .tag("cups", cups)
                .addField("production_kwh", production)
                .addField("obtain_method", "Real")
                .build());
    }

    private DatadisProductionMonthlyPoint singleMonthlyPoint(String cups, String startDate, String endDate) {
        List<DatadisProductionMonthlyPoint> result = queryMonthlyData(cups, startDate, endDate);
        assertEquals(1, result.size(), "Expected exactly one aggregated monthly point for cups " + cups);
        return result.get(0);
    }

    private List<DatadisProductionMonthlyPoint> queryMonthlyData(String cups, String startDate, String endDate) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            Query query = new Query(String.format(
                    "SELECT * FROM \"%s\" WHERE cups = '%s' AND time >= '%s' AND time <= '%s'",
                    DatadisProductionMeasurements.PRODUCTION_KWH_MONTH_MEASUREMENT, cups, startDate, endDate));
            QueryResult result = connection.query(query);
            InfluxDBResultMapper mapper = new InfluxDBResultMapper();
            return mapper.toPOJO(result, DatadisProductionMonthlyPoint.class);
        }
    }
}
