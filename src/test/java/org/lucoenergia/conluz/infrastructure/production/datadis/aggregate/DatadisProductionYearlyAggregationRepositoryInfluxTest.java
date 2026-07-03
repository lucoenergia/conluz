package org.lucoenergia.conluz.infrastructure.production.datadis.aggregate;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.datadis.aggregate.DatadisProductionYearlyAggregationRepository;
import org.lucoenergia.conluz.infrastructure.production.datadis.DatadisProductionMeasurements;
import org.lucoenergia.conluz.infrastructure.production.datadis.DatadisProductionYearlyPoint;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for {@link DatadisProductionYearlyAggregationRepositoryInflux}.
 * <p>
 * Yearly aggregation reads the {@code datadis_production_kwh_month} measurement, so the test seeds
 * monthly points (at the 1st of each month at 00:00:00 UTC, matching how the monthly aggregation's
 * year window {@code >= yyyy-01-01T00:00:00Z} includes January) for two supplies (CUPS), runs the
 * yearly aggregation, and asserts the correct per-cups yearly sums, the deterministic January 1st
 * timestamp, and idempotent re-run.
 * <p>
 * The aggregated record timestamp is January 1st at midnight local time (Europe/Madrid, UTC+1),
 * which is 2022-12-31T23:00:00Z in UTC.
 */
class DatadisProductionYearlyAggregationRepositoryInfluxTest extends BaseIntegrationTest {

    private static final String CUPS_A = "ES0031406912345678JN0F";
    private static final String CUPS_B = "ES0031406987654321AB1C";

    // Window wide enough to capture January 1 local midnight in any timezone.
    private static final String JAN_WINDOW_START = "2022-12-31T20:00:00Z";
    private static final String JAN_WINDOW_END = "2023-01-01T04:00:00Z";

    @Autowired
    private DatadisProductionYearlyAggregationRepository repository;

    @Autowired
    private InfluxDbConnectionManager influxDbConnectionManager;

    private Supply supplyA;
    private Supply supplyB;

    @BeforeEach
    void setUp() {
        User user = UserMother.randomUser();
        supplyA = SupplyMother.random(user).withCode(CUPS_A).build();
        supplyB = SupplyMother.random(user).withCode(CUPS_B).build();
        clearMeasurementsForCups();
        loadMonthlyDataFor2023();
    }

    @AfterEach
    void tearDown() {
        clearMeasurementsForCups();
    }

    @Test
    void testAggregateYearlyProductionComputesCorrectPerCupsSums() {

        // When - aggregate 2023 for both supplies
        repository.aggregateYearlyProduction(supplyA, 2023);
        repository.aggregateYearlyProduction(supplyB, 2023);

        // Then - each cups has exactly one yearly point holding its own 2023 sum
        DatadisProductionYearlyPoint pointA = singleYearlyPoint(CUPS_A, JAN_WINDOW_START, JAN_WINDOW_END);
        assertEquals(CUPS_A, pointA.getCups());
        assertNotNull(pointA.getProductionKWh());
        // 10.0 + 20.0 + 30.0 = 60.0
        assertEquals(60.0, pointA.getProductionKWh(), 0.001,
                "CUPS_A yearly production must sum its monthly points for 2023");
        assertEquals("Real", pointA.getObtainMethod());

        DatadisProductionYearlyPoint pointB = singleYearlyPoint(CUPS_B, JAN_WINDOW_START, JAN_WINDOW_END);
        assertEquals(CUPS_B, pointB.getCups());
        assertNotNull(pointB.getProductionKWh());
        // 5.0 + 15.0 = 20.0
        assertEquals(20.0, pointB.getProductionKWh(), 0.001,
                "CUPS_B yearly production must sum its monthly points for 2023");
    }

    @Test
    void testAggregateYearlyProductionSetsTimestampToJanuaryFirst() {

        // When
        repository.aggregateYearlyProduction(supplyA, 2023);

        // Then - point exists in the January 1 local-midnight window...
        List<DatadisProductionYearlyPoint> inWindow = queryYearlyData(CUPS_A, JAN_WINDOW_START, JAN_WINDOW_END);
        assertFalse(inWindow.isEmpty(),
                "Aggregated point should be stored at January 1st at midnight (local time)");

        // ...and NOT later in the year
        List<DatadisProductionYearlyPoint> afterJanuary = queryYearlyData(
                CUPS_A, "2023-01-01T04:00:00Z", "2023-12-31T23:59:59Z");
        assertTrue(afterJanuary.isEmpty(),
                "Aggregated point must not appear after January 1st");
    }

    @Test
    void testReRunningAggregationOverwritesInsteadOfDuplicating() {

        // When - aggregate the same year twice
        repository.aggregateYearlyProduction(supplyA, 2023);
        repository.aggregateYearlyProduction(supplyA, 2023);

        // Then - still exactly one point (deterministic timestamp overwrites) with the same value
        List<DatadisProductionYearlyPoint> result = queryYearlyData(CUPS_A, JAN_WINDOW_START, JAN_WINDOW_END);
        assertEquals(1, result.size(), "Re-running the aggregation must overwrite, not duplicate");
        assertEquals(60.0, result.get(0).getProductionKWh(), 0.001);
    }

    @Test
    void testAggregateYearlyProductionWithNoMonthlyDataDoesNotWrite() {

        // When - aggregate 2022, which has no monthly data loaded
        repository.aggregateYearlyProduction(supplyA, 2022);

        // Then - nothing is written to the yearly measurement for 2022
        List<DatadisProductionYearlyPoint> result = queryYearlyData(
                CUPS_A, "2021-12-31T20:00:00Z", "2022-01-01T04:00:00Z");
        assertTrue(result.isEmpty(),
                "No yearly data should be written when there is no monthly source data");
    }

    // -----------------------------------------------------------------------
    // Data setup helpers
    // -----------------------------------------------------------------------

    private void clearMeasurementsForCups() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            for (String measurement : List.of(
                    DatadisProductionMeasurements.PRODUCTION_KWH_MONTH_MEASUREMENT,
                    DatadisProductionMeasurements.PRODUCTION_KWH_YEAR_MEASUREMENT)) {
                for (String cups : List.of(CUPS_A, CUPS_B)) {
                    connection.query(new Query(String.format(
                            "DROP SERIES FROM \"%s\" WHERE \"cups\" = '%s'",
                            measurement, cups)));
                }
            }
        }
    }

    private void loadMonthlyDataFor2023() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();

            // CUPS_A - three monthly points in 2023: 10 + 20 + 30 = 60
            loadMonthlyPoint(batchPoints, CUPS_A, 1672531200000000000L, 10.0); // Jan 2023 00:00Z
            loadMonthlyPoint(batchPoints, CUPS_A, 1675209600000000000L, 20.0); // Feb 2023 00:00Z
            loadMonthlyPoint(batchPoints, CUPS_A, 1677628800000000000L, 30.0); // Mar 2023 00:00Z

            // CUPS_B - two monthly points in 2023: 5 + 15 = 20
            loadMonthlyPoint(batchPoints, CUPS_B, 1672531200000000000L, 5.0);  // Jan 2023 00:00Z
            loadMonthlyPoint(batchPoints, CUPS_B, 1675209600000000000L, 15.0); // Feb 2023 00:00Z

            connection.write(batchPoints);
        }
    }

    private void loadMonthlyPoint(BatchPoints batchPoints, String cups, long timestampNanos, double production) {
        batchPoints.point(Point.measurement(DatadisProductionMeasurements.PRODUCTION_KWH_MONTH_MEASUREMENT)
                .time(timestampNanos, TimeUnit.NANOSECONDS)
                .tag("cups", cups)
                .addField("production_kwh", production)
                .addField("obtain_method", "Real")
                .build());
    }

    private DatadisProductionYearlyPoint singleYearlyPoint(String cups, String startDate, String endDate) {
        List<DatadisProductionYearlyPoint> result = queryYearlyData(cups, startDate, endDate);
        assertEquals(1, result.size(), "Expected exactly one aggregated yearly point for cups " + cups);
        return result.get(0);
    }

    private List<DatadisProductionYearlyPoint> queryYearlyData(String cups, String startDate, String endDate) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            Query query = new Query(String.format(
                    "SELECT * FROM \"%s\" WHERE cups = '%s' AND time >= '%s' AND time <= '%s'",
                    DatadisProductionMeasurements.PRODUCTION_KWH_YEAR_MEASUREMENT, cups, startDate, endDate));
            QueryResult result = connection.query(query);
            InfluxDBResultMapper mapper = new InfluxDBResultMapper();
            return mapper.toPOJO(result, DatadisProductionYearlyPoint.class);
        }
    }
}
