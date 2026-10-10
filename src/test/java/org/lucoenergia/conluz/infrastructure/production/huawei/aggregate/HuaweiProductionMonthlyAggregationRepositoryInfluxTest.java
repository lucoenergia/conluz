package org.lucoenergia.conluz.infrastructure.production.huawei.aggregate;

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
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionMonthlyAggregationRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.infrastructure.production.HuaweiHourlyProductionMonthlyPoint;
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
 * Integration test for HuaweiProductionMonthlyAggregationRepositoryInflux.
 * <p>
 * Test data: 6 hourly records across two days in April 2023.
 * Each record has:
 *   inverter_power = 1.0 kWh  → expected sum: 6.0
 *   ongrid_power   = 0.8 kWh  → expected sum: 4.8
 *   theory_power   = 1.2 kWh  → expected sum: 7.2
 * <p>
 * The aggregated record timestamp is the first day of April 2023 at midnight local time (Europe/Madrid),
 * which is 2023-03-31T22:00:00Z in UTC.
 */
@SpringBootTest
class HuaweiProductionMonthlyAggregationRepositoryInfluxTest extends BaseIntegrationTest {

    private static final String STATION_CODE = "NE=12345678";

    @Autowired
    private HuaweiProductionMonthlyAggregationRepository repository;

    @Autowired
    private InfluxDbConnectionManager influxDbConnectionManager;

    private Plant plant;

    @BeforeEach
    void setUp() {
        plant = PlantMother.random().withProviderCode(STATION_CODE).build();
        loadHourlyDataForApril2023();
    }

    @AfterEach
    void tearDown() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            for (String measurement : List.of(
                    HuaweiConfig.HUAWEI_HOURLY_PRODUCTION_MEASUREMENT,
                    HuaweiConfig.HUAWEI_MONTHLY_PRODUCTION_MEASUREMENT)) {
                connection.query(new Query(String.format(
                        "DROP SERIES FROM \"%s\" WHERE \"station_code\" = '%s'",
                        measurement, STATION_CODE)));
            }
        }
    }

    @Test
    void testAggregateMonthlyProductionComputesCorrectSums() {

        // When
        repository.aggregateMonthlyProduction(plant, Month.APRIL, 2023);

        // Then - query using a window wide enough to capture April 1 midnight in any timezone
        List<HuaweiHourlyProductionMonthlyPoint> result = queryMonthlyData(
                "2023-03-31T20:00:00Z", "2023-04-01T04:00:00Z");

        assertFalse(result.isEmpty(), "Expected aggregated monthly data to be written for April 2023");
        assertEquals(1, result.size());

        HuaweiHourlyProductionMonthlyPoint point = result.get(0);
        assertEquals(STATION_CODE, point.getStationCode());

        // 6 records x 1.0 = 6.0
        assertNotNull(point.getInverterPower());
        assertEquals(6.0, point.getInverterPower(), 0.01,
                "Total inverter power should be the sum of all hourly records for April 2023");

        // 6 records x 0.8 = 4.8
        assertNotNull(point.getOngridPower());
        assertEquals(4.8, point.getOngridPower(), 0.01,
                "Total ongrid power should be the sum of all hourly records for April 2023");

        // 6 records x 1.2 = 7.2
        assertNotNull(point.getTheoryPower());
        assertEquals(7.2, point.getTheoryPower(), 0.01,
                "Total theory power should be the sum of all hourly records for April 2023");
    }

    @Test
    void testAggregateMonthlyProductionSetsTimestampToFirstDayOfMonth() {

        // When
        repository.aggregateMonthlyProduction(plant, Month.APRIL, 2023);

        // Then - the aggregated point must exist at first day of April midnight (local timezone)
        // April 1, 2023 00:00:00 Europe/Madrid (UTC+2) = 2023-03-31T22:00:00Z
        List<HuaweiHourlyProductionMonthlyPoint> inWindow = queryMonthlyData(
                "2023-03-31T20:00:00Z", "2023-04-01T04:00:00Z");

        assertFalse(inWindow.isEmpty(),
                "Aggregated point should be stored at the first day of the month at midnight (local time)");

        // Verify it does NOT appear in the previous month's window
        List<HuaweiHourlyProductionMonthlyPoint> previousMonth = queryMonthlyData(
                "2023-03-01T00:00:00Z", "2023-03-31T19:59:59Z");

        assertTrue(previousMonth.isEmpty(),
                "Aggregated point must not appear in the previous month's time window");
    }

    @Test
    void testAggregateMonthlyProductionWithNoHourlyDataDoesNotWrite() {

        // When - aggregate for January 2023, which has no hourly data loaded
        repository.aggregateMonthlyProduction(plant, Month.JANUARY, 2023);

        // Then - nothing should be written to the monthly measurement for January 2023
        List<HuaweiHourlyProductionMonthlyPoint> result = queryMonthlyData(
                "2022-12-31T20:00:00Z", "2023-01-01T04:00:00Z");

        assertTrue(result.isEmpty(),
                "No monthly data should be written when there is no hourly source data");
    }

    // -----------------------------------------------------------------------
    // Data setup helpers
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("HPA-001 the monthly Huawei production point sums the hourly records of the local month, in winter and summer time")
    void testAggregateMonthlyProductionSumsTheHourlyRecordsOfTheLocalMonth() {
        // Europe/Madrid. January 2026 (CET, UTC+1) is [2025-12-31T23:00Z, 2026-01-31T23:00Z);
        // July 2026 (CEST, UTC+2) is [2026-06-30T22:00Z, 2026-07-31T22:00Z).
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();
            loadHourlyPoint(batchPoints, "2025-12-31T22:00:00Z", 1000.0); // Dec 31 23:00 local
            loadHourlyPoint(batchPoints, "2025-12-31T23:00:00Z", 1.0);    // Jan 1 00:00 local
            loadHourlyPoint(batchPoints, "2026-01-31T22:00:00Z", 10.0);   // Jan 31 23:00 local
            loadHourlyPoint(batchPoints, "2026-01-31T23:00:00Z", 2000.0); // Feb 1 00:00 local
            loadHourlyPoint(batchPoints, "2026-06-30T21:00:00Z", 3000.0); // Jun 30 23:00 local
            loadHourlyPoint(batchPoints, "2026-06-30T22:00:00Z", 2.0);    // Jul 1 00:00 local
            loadHourlyPoint(batchPoints, "2026-07-31T21:00:00Z", 20.0);   // Jul 31 23:00 local
            loadHourlyPoint(batchPoints, "2026-07-31T22:00:00Z", 4000.0); // Aug 1 00:00 local
            connection.write(batchPoints);
        }

        repository.aggregateMonthlyProduction(plant, Month.JANUARY, 2026);
        repository.aggregateMonthlyProduction(plant, Month.JULY, 2026);

        List<HuaweiHourlyProductionMonthlyPoint> january = queryMonthlyData("2025-12-31T23:00:00Z", "2025-12-31T23:00:00Z");
        assertEquals(1, january.size(), "January is stamped at local midnight of the 1st");
        assertEquals(11.0, january.get(0).getInverterPower(), 0.001,
                "January must sum exactly the hours of the local month");
        List<HuaweiHourlyProductionMonthlyPoint> july = queryMonthlyData("2026-06-30T22:00:00Z", "2026-06-30T22:00:00Z");
        assertEquals(1, july.size(), "July is stamped at local midnight of the 1st");
        assertEquals(22.0, july.get(0).getInverterPower(), 0.001,
                "July must sum exactly the hours of the local month");
    }

    /**
     * Loads 6 hourly records across two days in April 2023.
     * Each record has inverter_power=1.0, ongrid_power=0.8, theory_power=1.2.
     * Expected sums: inverter_power=6.0, ongrid_power=4.8, theory_power=7.2
     */
    private void loadHourlyDataForApril2023() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();

            // April 1, 2023 - first 3 hours (timestamps in nanoseconds)
            loadHourlyPoint(batchPoints, 1680307200000000000L); // 2023-04-01T00:00:00Z
            loadHourlyPoint(batchPoints, 1680310800000000000L); // 2023-04-01T01:00:00Z
            loadHourlyPoint(batchPoints, 1680314400000000000L); // 2023-04-01T02:00:00Z

            // April 2, 2023 - first 3 hours
            loadHourlyPoint(batchPoints, 1680393600000000000L); // 2023-04-02T00:00:00Z
            loadHourlyPoint(batchPoints, 1680397200000000000L); // 2023-04-02T01:00:00Z
            loadHourlyPoint(batchPoints, 1680400800000000000L); // 2023-04-02T02:00:00Z

            connection.write(batchPoints);
        }
    }

    private void loadHourlyPoint(BatchPoints batchPoints, String instant, double inverterPower) {
        batchPoints.point(Point.measurement(HuaweiConfig.HUAWEI_HOURLY_PRODUCTION_MEASUREMENT)
                .time(Instant.parse(instant).toEpochMilli(), TimeUnit.MILLISECONDS)
                .tag("station_code", STATION_CODE)
                .addField("inverter_power", inverterPower)
                .addField("ongrid_power", 0.0)
                .addField("power_profit", 0.0)
                .addField("theory_power", 0.0)
                .addField("radiation_intensity", 0.0)
                .build());
    }

    private void loadHourlyPoint(BatchPoints batchPoints, long timestampNanos) {
        batchPoints.point(Point.measurement(HuaweiConfig.HUAWEI_HOURLY_PRODUCTION_MEASUREMENT)
                .time(timestampNanos, TimeUnit.NANOSECONDS)
                .tag("station_code", STATION_CODE)
                .addField("inverter_power", 1.0)
                .addField("ongrid_power", 0.8)
                .addField("power_profit", 0.5)
                .addField("theory_power", 1.2)
                .addField("radiation_intensity", 0.3)
                .build());
    }

    private List<HuaweiHourlyProductionMonthlyPoint> queryMonthlyData(String startDate, String endDate) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            Query query = new Query(String.format(
                    "SELECT * FROM \"%s\" WHERE station_code = '%s' AND time >= '%s' AND time <= '%s'",
                    HuaweiConfig.HUAWEI_MONTHLY_PRODUCTION_MEASUREMENT, STATION_CODE, startDate, endDate));
            QueryResult result = connection.query(query);
            InfluxDBResultMapper mapper = new InfluxDBResultMapper();
            return mapper.toPOJO(result, HuaweiHourlyProductionMonthlyPoint.class);
        }
    }
}
