package org.lucoenergia.conluz.infrastructure.production.huawei.aggregate;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionYearlyAggregationRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.infrastructure.production.HuaweiHourlyProductionYearlyPoint;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for HuaweiProductionYearlyAggregationRepositoryInflux.
 * <p>
 * Test data: 12 monthly records for 2023, one per month.
 * Each record has:
 *   inverter_power = 100.0 kWh  → expected sum: 1200.0
 *   ongrid_power   =  90.0 kWh  → expected sum: 1080.0
 *   theory_power   = 110.0 kWh  → expected sum: 1320.0
 * <p>
 * The aggregated record timestamp is January 1st, 2023 at midnight local time (Europe/Madrid),
 * which is 2022-12-31T23:00:00Z in UTC.
 */
@SpringBootTest
class HuaweiProductionYearlyAggregationRepositoryInfluxTest extends BaseIntegrationTest {

    private static final String STATION_CODE = "NE=87654321";

    @Autowired
    private HuaweiProductionYearlyAggregationRepository repository;

    @Autowired
    private InfluxDbConnectionManager influxDbConnectionManager;

    private Plant plant;

    @BeforeEach
    void setUp() {
        plant = PlantMother.random().withCode(STATION_CODE).build();
        clearMonthlyMeasurementForStation();
        loadMonthlyDataFor2023();
    }

    @AfterEach
    void tearDown() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            for (String measurement : List.of(
                    HuaweiConfig.HUAWEI_MONTHLY_PRODUCTION_MEASUREMENT,
                    HuaweiConfig.HUAWEI_YEARLY_PRODUCTION_MEASUREMENT)) {
                connection.query(new Query(String.format(
                        "DROP SERIES FROM \"%s\" WHERE \"station_code\" = '%s'",
                        measurement, STATION_CODE)));
            }
        }
    }

    @Test
    void testAggregateYearlyProductionComputesCorrectSums() {

        // When
        repository.aggregateYearlyProduction(plant, 2023);

        // Then - query using a window wide enough to capture January 1 midnight in any timezone
        List<HuaweiHourlyProductionYearlyPoint> result = queryYearlyData(
                "2022-12-31T20:00:00Z", "2023-01-01T04:00:00Z");

        assertFalse(result.isEmpty(), "Expected aggregated yearly data to be written for 2023");
        assertEquals(1, result.size());

        HuaweiHourlyProductionYearlyPoint point = result.get(0);
        assertEquals(STATION_CODE, point.getStationCode());

        // 12 records x 100.0 = 1200.0
        assertNotNull(point.getInverterPower());
        assertEquals(1200.0, point.getInverterPower(), 0.1,
                "Total inverter power should be the sum of all monthly records for 2023");

        // 12 records x 90.0 = 1080.0
        assertNotNull(point.getOngridPower());
        assertEquals(1080.0, point.getOngridPower(), 0.1,
                "Total ongrid power should be the sum of all monthly records for 2023");

        // 12 records x 110.0 = 1320.0
        assertNotNull(point.getTheoryPower());
        assertEquals(1320.0, point.getTheoryPower(), 0.1,
                "Total theory power should be the sum of all monthly records for 2023");
    }

    @Test
    void testAggregateYearlyProductionSetsTimestampToJanuaryFirst() {

        // When
        repository.aggregateYearlyProduction(plant, 2023);

        // Then - the aggregated point must exist at January 1 midnight (local timezone)
        List<HuaweiHourlyProductionYearlyPoint> inWindow = queryYearlyData(
                "2022-12-30T20:00:00Z", "2023-01-01T04:00:00Z");

        assertFalse(inWindow.isEmpty(),
                "Aggregated point should be stored at January 1st at midnight (local time)");

        // Verify it does NOT appear later in the year
        List<HuaweiHourlyProductionYearlyPoint> afterJanuary = queryYearlyData(
                "2023-01-01T04:00:00Z", "2023-12-31T23:59:59Z");

        assertTrue(afterJanuary.isEmpty(),
                "Aggregated point must not appear after January 1st");
    }

    @Test
    void testAggregateYearlyProductionWithNoMonthlyDataDoesNotWrite() {

        // When - aggregate for 2022, which has no monthly data loaded
        repository.aggregateYearlyProduction(plant, 2022);

        // Then - nothing should be written to the yearly measurement for 2022
        List<HuaweiHourlyProductionYearlyPoint> result = queryYearlyData(
                "2022-12-30T20:00:00Z", "2022-12-31T04:00:00Z");

        assertTrue(result.isEmpty(),
                "No yearly data should be written when there is no monthly source data");
    }

    // -----------------------------------------------------------------------
    // Data setup helpers
    // -----------------------------------------------------------------------

    /**
     * Removes all monthly series for the test station code to prevent data pollution.
     */
    private void clearMonthlyMeasurementForStation() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            connection.query(new Query(String.format(
                    "DROP SERIES FROM \"%s\" WHERE \"station_code\" = '%s'",
                    HuaweiConfig.HUAWEI_MONTHLY_PRODUCTION_MEASUREMENT, STATION_CODE)));
        }
    }

    /**
     * Loads 12 monthly records for 2023, one per month (1st of each month at 00:00:00 UTC).
     * Each record: inverter_power=100.0, ongrid_power=90.0, theory_power=110.0
     * Expected sums: inverter_power=1200.0, ongrid_power=1080.0, theory_power=1320.0
     */
    private void loadMonthlyDataFor2023() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();

            loadMonthlyPoint(batchPoints, 1672531200000000000L);  // 2023-01-01T00:00:00Z
            loadMonthlyPoint(batchPoints, 1675209600000000000L);  // 2023-02-01T00:00:00Z
            loadMonthlyPoint(batchPoints, 1677628800000000000L);  // 2023-03-01T00:00:00Z
            loadMonthlyPoint(batchPoints, 1680307200000000000L);  // 2023-04-01T00:00:00Z
            loadMonthlyPoint(batchPoints, 1682899200000000000L);  // 2023-05-01T00:00:00Z
            loadMonthlyPoint(batchPoints, 1685577600000000000L);  // 2023-06-01T00:00:00Z
            loadMonthlyPoint(batchPoints, 1688169600000000000L);  // 2023-07-01T00:00:00Z
            loadMonthlyPoint(batchPoints, 1690848000000000000L);  // 2023-08-01T00:00:00Z
            loadMonthlyPoint(batchPoints, 1693526400000000000L);  // 2023-09-01T00:00:00Z
            loadMonthlyPoint(batchPoints, 1696118400000000000L);  // 2023-10-01T00:00:00Z
            loadMonthlyPoint(batchPoints, 1698796800000000000L);  // 2023-11-01T00:00:00Z
            loadMonthlyPoint(batchPoints, 1701388800000000000L);  // 2023-12-01T00:00:00Z

            connection.write(batchPoints);
        }
    }

    private void loadMonthlyPoint(BatchPoints batchPoints, long timestampNanos) {
        batchPoints.point(Point.measurement(HuaweiConfig.HUAWEI_MONTHLY_PRODUCTION_MEASUREMENT)
                .time(timestampNanos, TimeUnit.NANOSECONDS)
                .tag("station_code", STATION_CODE)
                .addField("inverter_power", 100.0)
                .addField("ongrid_power", 90.0)
                .addField("power_profit", 50.0)
                .addField("theory_power", 110.0)
                .addField("radiation_intensity", 30.0)
                .build());
    }

    private List<HuaweiHourlyProductionYearlyPoint> queryYearlyData(String startDate, String endDate) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            Query query = new Query(String.format(
                    "SELECT * FROM \"%s\" WHERE station_code = '%s' AND time >= '%s' AND time <= '%s'",
                    HuaweiConfig.HUAWEI_YEARLY_PRODUCTION_MEASUREMENT, STATION_CODE, startDate, endDate));
            QueryResult result = connection.query(query);
            InfluxDBResultMapper mapper = new InfluxDBResultMapper();
            return mapper.toPOJO(result, HuaweiHourlyProductionYearlyPoint.class);
        }
    }
}
