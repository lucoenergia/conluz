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
import org.lucoenergia.conluz.infrastructure.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for DatadisYearlyAggregationRepositoryInflux.
 * <p>
 * Test data: 12 monthly records for 2023, each stamped at local midnight on the 1st of its month
 * exactly as {@code DatadisMonthlyAggregationRepositoryInflux} stamps them -- which is what makes the
 * year window a local-calendar one rather than a UTC one. January's point sits at 2022-12-31T23:00Z,
 * before the UTC year even begins.
 * Expected sums after aggregation:
 *   consumption_kwh:             4205.8
 *   surplus_energy_kwh:           728.0
 *   self_consumption_energy_kwh: 1518.0
 * <p>
 * The aggregated record timestamp is January 1, 2023 at midnight local time (Europe/Madrid, UTC+1),
 * which is 2022-12-31T23:00:00Z in UTC.
 */
class DatadisYearlyAggregationRepositoryInfluxTest extends BaseIntegrationTest {

    private static final String CUPS_CODE = "ES0031406912345678JN0F";
    private static final String SECOND_CUPS_CODE = "ES0031406912345678KN0F";

    /** Local midnight on January 1, 2023 in Europe/Madrid (UTC+1 in winter). */
    private static final String START_OF_2023 = "2022-12-31T23:00:00Z";
    /** Local midnight on January 1, 2024, the exclusive end of the same window. */
    private static final String START_OF_2024 = "2023-12-31T23:00:00Z";

    @Autowired
    private DatadisYearlyAggregationRepository repository;

    @Autowired
    private InfluxDbConnectionManager influxDbConnectionManager;

    private Supply supply;
    private Supply secondSupply;

    @BeforeEach
    void setUp() {
        User user = UserMother.randomUser();
        supply = SupplyMother.random(user).withCode(CUPS_CODE).build();
        secondSupply = SupplyMother.random(user).withCode(SECOND_CUPS_CODE).build();
        clearMonthlyMeasurementForCups();
        loadMonthlyDataFor2023();
    }

    @AfterEach
    void tearDown() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            for (String measurement : List.of(
                    DatadisConfigEntity.CONSUMPTION_KWH_MONTH_MEASUREMENT,
                    DatadisConfigEntity.CONSUMPTION_KWH_YEAR_MEASUREMENT)) {
                for (String cups : List.of(CUPS_CODE, SECOND_CUPS_CODE)) {
                    connection.query(new Query(String.format(
                            "DROP SERIES FROM \"%s\" WHERE \"cups\" = '%s'",
                            measurement, cups)));
                }
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
    void testAggregateYearlyConsumptionSetsTimestampToJanuaryFirst() {

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

        // Then - nothing should be written at local midnight on January 1, 2022 (2021-12-31T23:00Z)
        List<DatadisConsumptionYearlyPoint> result = queryYearlyData(
                "2021-12-31T20:00:00Z", "2022-01-01T04:00:00Z");

        assertTrue(result.isEmpty(),
                "No yearly data should be written when there is no monthly source data");
    }

    /**
     * AC3, at the year boundary. The 2023 window runs from local midnight on January 1, 2023 to local
     * midnight on January 1, 2024, so December 2022's and January 2024's pre-aggregates stay out of
     * it. January 2024's is the telling one: stamped at 2023-12-31T23:00Z, the literal UTC window
     * {@code <= 2023-12-31T23:59:59Z} used to swallow it -- while excluding January 2023's, stamped
     * at 2022-12-31T23:00Z, which is the reason every stored yearly total ran from February.
     */
    @Test
    void testAggregateYearlyConsumptionUsesLocalYearBoundaries() {

        // Given - the neighbouring years' adjoining months
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();
            loadMonthlyPoint(batchPoints, "2022-11-30T23:00:00Z", 1000.0, 0.0, 0.0);  // Dec 2022
            loadMonthlyPoint(batchPoints, "2023-12-31T23:00:00Z", 2000.0, 0.0, 0.0);  // Jan 2024
            connection.write(batchPoints);
        }

        // When
        repository.aggregateYearlyConsumption(supply, 2023);

        // Then - still the twelve months of 2023 and nothing else
        List<DatadisConsumptionYearlyPoint> result = queryYearlyData(START_OF_2023, START_OF_2023);

        assertEquals(1, result.size());
        assertEquals(4205.8, result.get(0).getConsumptionKWh(), 0.1,
                "Neither December 2022 nor January 2024 may be counted in 2023");
    }

    /**
     * AC8. Aggregating the same year twice for two supplies leaves exactly one point per supply and
     * period: the timestamp and the tag set are the same on every run, so InfluxDB overwrites.
     */
    @Test
    void testAggregatingTheSameYearTwiceLeavesOnePointPerSupply() {

        // Given - monthly data for a second supply as well
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();
            loadMonthlyPoint(batchPoints, SECOND_CUPS_CODE, "2022-12-31T23:00:00Z", 100.0, 0.0, 0.0);
            loadMonthlyPoint(batchPoints, SECOND_CUPS_CODE, "2023-05-31T22:00:00Z", 200.0, 0.0, 0.0);
            connection.write(batchPoints);
        }

        // When
        for (int run = 0; run < 2; run++) {
            repository.aggregateYearlyConsumption(supply, 2023);
            repository.aggregateYearlyConsumption(secondSupply, 2023);
        }

        // Then
        assertEquals(1, queryYearlyData(CUPS_CODE, START_OF_2023, START_OF_2024).size());

        List<DatadisConsumptionYearlyPoint> secondSupplyPoints =
                queryYearlyData(SECOND_CUPS_CODE, START_OF_2023, START_OF_2024);
        assertEquals(1, secondSupplyPoints.size());
        assertEquals(300.0, secondSupplyPoints.get(0).getConsumptionKWh(), 0.1,
                "The second supply keeps its own total, unaffected by the first");
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
            for (String cups : List.of(CUPS_CODE, SECOND_CUPS_CODE)) {
                connection.query(new Query(String.format(
                        "DROP SERIES FROM \"%s\" WHERE \"cups\" = '%s'",
                        DatadisConfigEntity.CONSUMPTION_KWH_MONTH_MEASUREMENT, cups)));
            }
        }
    }

    /**
     * Loads 12 monthly records for 2023, each at local midnight on the 1st of its month, which is
     * where DatadisMonthlyAggregationRepositoryInflux writes them: the previous day at 23:00Z while
     * Europe/Madrid is on CET, at 22:00Z while it is on CEST (from March 26 to October 29 in 2023).
     * Sums: consumption=4205.8, surplus=728.0, self_consumption=1518.0
     */
    private void loadMonthlyDataFor2023() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();

            loadMonthlyPoint(batchPoints, "2022-12-31T23:00:00Z", 450.5, 25.0,  85.0);  // Jan 2023
            loadMonthlyPoint(batchPoints, "2023-01-31T23:00:00Z", 420.3, 30.0,  90.0);  // Feb 2023
            loadMonthlyPoint(batchPoints, "2023-02-28T23:00:00Z", 380.7, 45.0, 110.0);  // Mar 2023
            loadMonthlyPoint(batchPoints, "2023-03-31T22:00:00Z", 330.2, 65.0, 135.0);  // Apr 2023
            loadMonthlyPoint(batchPoints, "2023-04-30T22:00:00Z", 290.8, 85.0, 155.0);  // May 2023
            loadMonthlyPoint(batchPoints, "2023-05-31T22:00:00Z", 270.5, 95.0, 165.0);  // Jun 2023
            loadMonthlyPoint(batchPoints, "2023-06-30T22:00:00Z", 285.3, 98.0, 168.0);  // Jul 2023
            loadMonthlyPoint(batchPoints, "2023-07-31T22:00:00Z", 295.6, 92.0, 162.0);  // Aug 2023
            loadMonthlyPoint(batchPoints, "2023-08-31T22:00:00Z", 310.4, 75.0, 145.0);  // Sep 2023
            loadMonthlyPoint(batchPoints, "2023-09-30T22:00:00Z", 340.8, 55.0, 120.0);  // Oct 2023
            loadMonthlyPoint(batchPoints, "2023-10-31T23:00:00Z", 390.5, 35.0,  95.0);  // Nov 2023
            loadMonthlyPoint(batchPoints, "2023-11-30T23:00:00Z", 440.2, 28.0,  88.0);  // Dec 2023

            connection.write(batchPoints);
        }
    }

    private void loadMonthlyPoint(BatchPoints batchPoints, String utcInstant,
                                   double consumption, double surplus, double selfConsumption) {
        loadMonthlyPoint(batchPoints, CUPS_CODE, utcInstant, consumption, surplus, selfConsumption);
    }

    private void loadMonthlyPoint(BatchPoints batchPoints, String cups, String utcInstant,
                                   double consumption, double surplus, double selfConsumption) {
        batchPoints.point(Point.measurement(DatadisConfigEntity.CONSUMPTION_KWH_MONTH_MEASUREMENT)
                .time(Instant.parse(utcInstant).toEpochMilli(), TimeUnit.MILLISECONDS)
                .tag("cups", cups)
                .addField("consumption_kwh", consumption)
                .addField("surplus_energy_kwh", surplus)
                .addField("self_consumption_energy_kwh", selfConsumption)
                .addField("generation_energy_kwh", 0.0)
                .addField("obtain_method", "Real")
                .build());
    }

    private List<DatadisConsumptionYearlyPoint> queryYearlyData(String startDate, String endDate) {
        return queryYearlyData(CUPS_CODE, startDate, endDate);
    }

    private List<DatadisConsumptionYearlyPoint> queryYearlyData(String cups, String startDate, String endDate) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            Query query = new Query(String.format(
                    "SELECT * FROM \"%s\" WHERE cups = '%s' AND time >= '%s' AND time <= '%s'",
                    DatadisConfigEntity.CONSUMPTION_KWH_YEAR_MEASUREMENT, cups, startDate, endDate));
            QueryResult result = connection.query(query);
            InfluxDBResultMapper mapper = new InfluxDBResultMapper();
            return mapper.toPOJO(result, DatadisConsumptionYearlyPoint.class);
        }
    }
}
