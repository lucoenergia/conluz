package org.lucoenergia.conluz.infrastructure.consumption.datadis.aggregate;

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
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisYearlyAggregationRepository;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConsumptionYearlyPoint;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for DatadisYearlyAggregationRepositoryInflux.
 * <p>
 * Test data: 12 monthly records for 2023.
 * Expected sums after aggregation:
 *   consumption_kwh:             4205.8
 *   surplus_energy_kwh:           728.0
 *   self_consumption_energy_kwh: 1518.0
 * <p>
 * The aggregated record timestamp is December 31, 2023 at midnight local time (Europe/Madrid, UTC+1),
 * which is 2023-12-30T23:00:00Z in UTC.
 */
class DatadisYearlyAggregationRepositoryInfluxTest extends BaseIntegrationTest {

    private static final String CUPS_CODE = "ES0031406912345678JN0F";

    @Autowired
    private DatadisYearlyAggregationRepository repository;

    @Autowired
    private InfluxDbConnectionManager influxDbConnectionManager;

    private Supply supply;

    @BeforeEach
    void setUp() {
        User user = UserMother.randomUser();
        supply = SupplyMother.random(user).withCode(CUPS_CODE).build();
        clearMonthlyMeasurementForCups();
        loadMonthlyDataFor2023();
    }

    @AfterEach
    void tearDown() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            for (String measurement : List.of(
                    DatadisConfigEntity.CONSUMPTION_KWH_MONTH_MEASUREMENT,
                    DatadisConfigEntity.CONSUMPTION_KWH_YEAR_MEASUREMENT)) {
                connection.query(new Query(String.format(
                        "DROP SERIES FROM \"%s\" WHERE \"cups\" = '%s'",
                        measurement, CUPS_CODE)));
            }
        }
    }

    @Test
    void testAggregateYearlyConsumptionComputesCorrectSums() {

        // When
        repository.aggregateYearlyConsumption(supply, 2023);

        // Then - query using a window wide enough to capture January 1 midnight in any timezone
        List<DatadisConsumptionYearlyPoint> result = queryYearlyData(
                "2022-12-31T20:00:00Z", "2023-01-01T04:00:00Z");

        assertFalse(result.isEmpty(), "Expected aggregated yearly data to be written for 2023");
        assertEquals(1, result.size());

        DatadisConsumptionYearlyPoint point = result.get(0);
        assertEquals(CUPS_CODE, point.getCups());

        // Sum of all 12 monthly consumption values = 4205.8
        assertNotNull(point.getConsumptionKWh());
        assertEquals(4205.8, point.getConsumptionKWh(), 0.1,
                "Total consumption should be the sum of all monthly records for 2023");

        // Sum of all 12 monthly surplus values = 728.0
        assertNotNull(point.getSurplusEnergyKWh());
        assertEquals(728.0, point.getSurplusEnergyKWh(), 0.1,
                "Total surplus should be the sum of all monthly surplus values for 2023");

        // Sum of all 12 monthly self-consumption values = 1518.0
        assertNotNull(point.getSelfConsumptionEnergyKWh());
        assertEquals(1518.0, point.getSelfConsumptionEnergyKWh(), 0.1,
                "Total self-consumption should be the sum of all monthly self-consumption values for 2023");

        assertNotNull(point.getObtainMethod());
        assertEquals("Real", point.getObtainMethod());
    }

    @Test
    void testAggregateYearlyConsumptionSetsTimestampToDecember31st() {

        // When
        repository.aggregateYearlyConsumption(supply, 2023);

        // Then - the aggregated point must exist at January 1 midnight (local timezone)
        List<DatadisConsumptionYearlyPoint> inWindow = queryYearlyData(
                "2022-12-30T20:00:00Z", "2023-01-01T04:00:00Z");

        assertFalse(inWindow.isEmpty(),
                "Aggregated point should be stored at January 1st at midnight (local time)");

        // Verify it does NOT appear later in the year
        List<DatadisConsumptionYearlyPoint> afterJanuary = queryYearlyData(
                "2023-01-01T04:00:00Z", "2023-12-31T23:59:59Z");

        assertTrue(afterJanuary.isEmpty(),
                "Aggregated point must not appear after January 1st");
    }

    @Test
    void testAggregateYearlyConsumptionWithNoMonthlyDataDoesNotWrite() {

        // When - aggregate for 2022, which has no monthly data loaded
        repository.aggregateYearlyConsumption(supply, 2022);

        // Then - nothing should be written to the yearly measurement for 2022
        List<DatadisConsumptionYearlyPoint> result = queryYearlyData(
                "2022-12-30T20:00:00Z", "2022-12-31T04:00:00Z");

        assertTrue(result.isEmpty(),
                "No yearly data should be written when there is no monthly source data");
    }

    // -----------------------------------------------------------------------
    // Data setup helpers
    // -----------------------------------------------------------------------

    /**
     * Removes all monthly series for the test CUPS code to prevent data pollution
     * from other tests that may write to the same measurement.
     */
    private void clearMonthlyMeasurementForCups() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            connection.query(new Query(String.format(
                    "DROP SERIES FROM \"%s\" WHERE \"cups\" = '%s'",
                    DatadisConfigEntity.CONSUMPTION_KWH_MONTH_MEASUREMENT, CUPS_CODE)));
        }
    }

    /**
     * Loads 12 monthly records for 2023 (same data as DatadisConsumptionInfluxLoader).
     * Sums: consumption=4205.8, surplus=728.0, self_consumption=1518.0
     */
    private void loadMonthlyDataFor2023() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();

            // Each timestamp is the 1st of the month at 00:00:00 UTC (nanoseconds)
            loadMonthlyPoint(batchPoints, 1672531200000000000L, 450.5, 25.0,  85.0);  // Jan 2023
            loadMonthlyPoint(batchPoints, 1675209600000000000L, 420.3, 30.0,  90.0);  // Feb 2023
            loadMonthlyPoint(batchPoints, 1677628800000000000L, 380.7, 45.0, 110.0);  // Mar 2023
            loadMonthlyPoint(batchPoints, 1680307200000000000L, 330.2, 65.0, 135.0);  // Apr 2023
            loadMonthlyPoint(batchPoints, 1682899200000000000L, 290.8, 85.0, 155.0);  // May 2023
            loadMonthlyPoint(batchPoints, 1685577600000000000L, 270.5, 95.0, 165.0);  // Jun 2023
            loadMonthlyPoint(batchPoints, 1688169600000000000L, 285.3, 98.0, 168.0);  // Jul 2023
            loadMonthlyPoint(batchPoints, 1690848000000000000L, 295.6, 92.0, 162.0);  // Aug 2023
            loadMonthlyPoint(batchPoints, 1693526400000000000L, 310.4, 75.0, 145.0);  // Sep 2023
            loadMonthlyPoint(batchPoints, 1696118400000000000L, 340.8, 55.0, 120.0);  // Oct 2023
            loadMonthlyPoint(batchPoints, 1698796800000000000L, 390.5, 35.0,  95.0);  // Nov 2023
            loadMonthlyPoint(batchPoints, 1701388800000000000L, 440.2, 28.0,  88.0);  // Dec 2023

            connection.write(batchPoints);
        }
    }

    private void loadMonthlyPoint(BatchPoints batchPoints, long timestampNanos,
                                   double consumption, double surplus, double selfConsumption) {
        batchPoints.point(Point.measurement(DatadisConfigEntity.CONSUMPTION_KWH_MONTH_MEASUREMENT)
                .time(timestampNanos, TimeUnit.NANOSECONDS)
                .tag("cups", CUPS_CODE)
                .addField("consumption_kwh", consumption)
                .addField("surplus_energy_kwh", surplus)
                .addField("self_consumption_energy_kwh", selfConsumption)
                .addField("generation_energy_kwh", 0.0)
                .addField("obtain_method", "Real")
                .build());
    }

    private List<DatadisConsumptionYearlyPoint> queryYearlyData(String startDate, String endDate) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            Query query = new Query(String.format(
                    "SELECT * FROM \"%s\" WHERE cups = '%s' AND time >= '%s' AND time <= '%s'",
                    DatadisConfigEntity.CONSUMPTION_KWH_YEAR_MEASUREMENT, CUPS_CODE, startDate, endDate));
            QueryResult result = connection.query(query);
            InfluxDBResultMapper mapper = new InfluxDBResultMapper();
            return mapper.toPOJO(result, DatadisConsumptionYearlyPoint.class);
        }
    }
}
