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
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisMonthlyAggregationRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConsumptionMonthlyPoint;
import org.lucoenergia.conluz.infrastructure.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.Month;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for DatadisMonthlyAggregationRepositoryInflux.
 * <p>
 * Test data: 30 hourly records for April 2023 (24 for April 1 + 6 for April 2).
 * Expected sums after aggregation:
 *   consumption_kwh:         15.57 (13.31 April 1 + 2.26 April 2)
 *   surplus_energy_kwh:       1.93 (only April 1 has solar production)
 *   self_consumption_energy_kwh: 2.37 (only April 1 has self-consumption)
 * <p>
 * The aggregated record timestamp is the first day of April 2023 at midnight local time (Europe/Madrid),
 * which is 2023-03-31T22:00:00Z in UTC.
 */
@SpringBootTest
class DatadisMonthlyAggregationRepositoryInfluxTest extends BaseIntegrationTest {

    private static final String CUPS_CODE = "ES0031406912345678JN0F";
    private static final String SECOND_CUPS_CODE = "ES0031406912345678KN0F";

    @Autowired
    private DatadisMonthlyAggregationRepository repository;

    @Autowired
    @Qualifier("getDatadisConsumptionRepositoryInflux")
    private GetDatadisConsumptionRepository getDatadisConsumptionRepository;

    @Autowired
    private InfluxDbConnectionManager influxDbConnectionManager;

    private Supply supply;
    private Supply secondSupply;

    @BeforeEach
    void setUp() {
        User user = UserMother.randomUser();
        supply = SupplyMother.random(user).withCode(CUPS_CODE).build();
        secondSupply = SupplyMother.random(user).withCode(SECOND_CUPS_CODE).build();
        loadHourlyDataForApril2023();
    }

    @AfterEach
    void tearDown() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            for (String measurement : List.of(
                    DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT,
                    DatadisConfigEntity.CONSUMPTION_KWH_MONTH_MEASUREMENT)) {
                for (String cups : List.of(CUPS_CODE, SECOND_CUPS_CODE)) {
                    connection.query(new Query(String.format(
                            "DROP SERIES FROM \"%s\" WHERE \"cups\" = '%s'",
                            measurement, cups)));
                }
            }
        }
    }

    @Test
    void testAggregateMonthlyConsumptionComputesCorrectSums() {

        // When
        repository.aggregateMonthlyConsumption(supply, Month.APRIL, 2023);

        // Then - query using a window wide enough to capture the April 1 midnight in any timezone
        List<DatadisConsumptionMonthlyPoint> result = queryMonthlyData(
                "2023-03-31T20:00:00Z", "2023-04-01T04:00:00Z");

        assertFalse(result.isEmpty(), "Expected aggregated monthly data to be written for April 2023");
        assertEquals(1, result.size());

        DatadisConsumptionMonthlyPoint point = result.get(0);
        assertEquals(CUPS_CODE, point.getCups());

        // April 1 sum: 13.31 + April 2 sum: 2.26 = 15.57
        assertNotNull(point.getConsumptionKWh());
        assertEquals(15.57, point.getConsumptionKWh(), 0.01,
                "Total consumption should be the sum of all hourly records for April 2023");

        // Surplus only on April 1 = 1.93
        assertNotNull(point.getSurplusEnergyKWh());
        assertEquals(1.93, point.getSurplusEnergyKWh(), 0.01,
                "Total surplus should be the sum of all hourly surplus values for April 2023");

        // Self-consumption only on April 1 = 2.37
        assertNotNull(point.getSelfConsumptionEnergyKWh());
        assertEquals(2.37, point.getSelfConsumptionEnergyKWh(), 0.01,
                "Total self-consumption should be the sum of all hourly self-consumption values for April 2023");

        assertNotNull(point.getObtainMethod());
        assertEquals("Real", point.getObtainMethod());
    }

    @Test
    void testAggregateMonthlyConsumptionSetsTimestampToFirstDayOfMonth() {

        // When
        repository.aggregateMonthlyConsumption(supply, Month.APRIL, 2023);

        // Then - the aggregated point must exist at first day of April midnight (local timezone)
        // April 1, 2023 00:00:00 Europe/Madrid (UTC+2) = 2023-03-31T22:00:00Z
        List<DatadisConsumptionMonthlyPoint> inWindow = queryMonthlyData(
                "2023-03-31T20:00:00Z", "2023-04-01T04:00:00Z");

        assertFalse(inWindow.isEmpty(),
                "Aggregated point should be stored at the first day of the month at midnight (local time)");

        // Verify it does NOT appear in the previous month's window
        List<DatadisConsumptionMonthlyPoint> previousMonth = queryMonthlyData(
                "2023-03-01T00:00:00Z", "2023-03-31T19:59:59Z");

        assertTrue(previousMonth.isEmpty(),
                "Aggregated point must not appear in the previous month's time window");
    }

    @Test
    void testAggregateMonthlyConsumptionWithNoHourlyDataDoesNotWrite() {

        // When - aggregate for January 2023, which has no hourly data loaded
        repository.aggregateMonthlyConsumption(supply, Month.JANUARY, 2023);

        // Then - nothing should be written to the monthly measurement for January 2023
        List<DatadisConsumptionMonthlyPoint> result = queryMonthlyData(
                "2022-12-31T20:00:00Z", "2023-01-01T04:00:00Z");

        assertTrue(result.isEmpty(),
                "No monthly data should be written when there is no hourly source data");
    }

    /**
     * AC3. January 2023 runs from local midnight on the 1st (2022-12-31T23:00Z, Europe/Madrid being
     * UTC+1 in winter) to local midnight on February 1st (2023-01-31T23:00Z). The records in the
     * first and last local hour of the month count towards it, and the ones in the neighbouring
     * months' adjoining local hours do not.
     */
    @Test
    void testAggregateMonthlyConsumptionUsesLocalMonthBoundaries() {

        loadWinterBoundaryRecords();

        repository.aggregateMonthlyConsumption(supply, Month.JANUARY, 2023);
        repository.aggregateMonthlyConsumption(supply, Month.DECEMBER, 2022);
        repository.aggregateMonthlyConsumption(supply, Month.FEBRUARY, 2023);

        // 1.0 (first local hour) + 2.0 (mid-month) + 4.0 (last local hour)
        assertEquals(7.0, consumptionOfMonth("2023-01-01T00:00:00+01:00"), 0.0,
                "January must hold its first and last local hour and nothing from its neighbours");
        assertEquals(100.0, consumptionOfMonth("2022-12-01T00:00:00+01:00"), 0.0,
                "The record in December's last local hour belongs to December");
        assertEquals(200.0, consumptionOfMonth("2023-02-01T00:00:00+01:00"), 0.0,
                "The record in February's first local hour belongs to February");
    }

    /**
     * AC6. A monthly point written by the old UTC-window logic -- which missed January's first local
     * hour and swallowed February's -- is replaced by the local-calendar total when recomputed. The
     * timestamp is unchanged, so this is an overwrite, not a second point.
     */
    @Test
    void testRecomputingReplacesAPreAggregateWrittenByTheOldLogic() {

        loadWinterBoundaryRecords();
        // What the literal UTC window [2023-01-01T00:00:00Z, 2023-01-31T23:59:00Z] summed: the
        // mid-month record, the last local hour, and February's first local hour, but not January's.
        writeMonthlyPoint(CUPS_CODE, "2023-01-01T00:00:00+01:00", 206.0);

        assertEquals(206.0, consumptionOfMonth("2023-01-01T00:00:00+01:00"), 0.0);

        repository.aggregateMonthlyConsumption(supply, Month.JANUARY, 2023);

        assertEquals(7.0, consumptionOfMonth("2023-01-01T00:00:00+01:00"), 0.0,
                "Recomputing must leave the local-calendar total in place of the old one");
        assertEquals(1, monthlyPointsOfJanuary2023(CUPS_CODE).size(),
                "Recomputing must overwrite the existing point, not add a second one");
    }

    /**
     * AC8. Aggregating the same month twice for two supplies leaves exactly one point per supply and
     * period: InfluxDB keys a point by measurement, tag set and timestamp, and none of the three
     * changes between runs.
     */
    @Test
    void testAggregatingTheSameMonthTwiceLeavesOnePointPerSupply() {

        loadFullLocalJanuary2023();

        for (int run = 0; run < 2; run++) {
            repository.aggregateMonthlyConsumption(supply, Month.JANUARY, 2023);
            repository.aggregateMonthlyConsumption(secondSupply, Month.JANUARY, 2023);
        }

        assertEquals(1, monthlyPointsOfJanuary2023(CUPS_CODE).size());
        assertEquals(1, monthlyPointsOfJanuary2023(SECOND_CUPS_CODE).size());
    }

    /**
     * AC4. Over a full local month of hourly records, the daily buckets add up to exactly the monthly
     * pre-aggregate, for each of two supplies. Every seeded value is a multiple of 0.25 kWh, so both
     * sides are exact in binary floating point and the comparison needs no tolerance.
     */
    @Test
    void testDailyTotalsAddUpToTheMonthlyTotal() {

        loadFullLocalJanuary2023();

        repository.aggregateMonthlyConsumption(supply, Month.JANUARY, 2023);
        repository.aggregateMonthlyConsumption(secondSupply, Month.JANUARY, 2023);

        double firstMonthly = consumptionOfMonth(CUPS_CODE, "2023-01-01T00:00:00+01:00");
        double secondMonthly = consumptionOfMonth(SECOND_CUPS_CODE, "2023-01-01T00:00:00+01:00");

        assertEquals(sumOfDailyTotals(supply), firstMonthly, 0.0,
                "The daily buckets of the local month must add up to the monthly total");
        assertEquals(sumOfDailyTotals(secondSupply), secondMonthly, 0.0,
                "The daily buckets of the local month must add up to the monthly total");
        assertNotEquals(firstMonthly, secondMonthly,
                "The two supplies must carry different data, or the comparison proves nothing");
    }

    private double sumOfDailyTotals(Supply aSupply) {
        List<DatadisConsumption> dailyTotals = getDatadisConsumptionRepository.getDailyConsumptionsByRangeOfDates(
                aSupply,
                OffsetDateTime.parse("2023-01-01T00:00:00+01:00"),
                OffsetDateTime.parse("2023-01-31T23:59:59+01:00"));

        assertEquals(31, dailyTotals.size(), "January has 31 local days");

        float total = 0.0f;
        for (DatadisConsumption dailyTotal : dailyTotals) {
            total += dailyTotal.getConsumptionKWh();
        }
        return total;
    }

    // -----------------------------------------------------------------------
    // Data setup helpers
    // -----------------------------------------------------------------------

    /**
     * Loads 30 hourly records for April 2023 (same data as DatadisConsumptionInfluxLoader).
     * 24 records for April 1 and 6 records for April 2.
     */
    private void loadHourlyDataForApril2023() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();

            // April 1, 2023 - 24 hourly records
            loadHourlyPoint(batchPoints, 1680307200000000000L, 0.45, 0.0,  0.0);
            loadHourlyPoint(batchPoints, 1680310800000000000L, 0.42, 0.0,  0.0);
            loadHourlyPoint(batchPoints, 1680314400000000000L, 0.38, 0.0,  0.0);
            loadHourlyPoint(batchPoints, 1680318000000000000L, 0.35, 0.0,  0.0);
            loadHourlyPoint(batchPoints, 1680321600000000000L, 0.33, 0.0,  0.0);
            loadHourlyPoint(batchPoints, 1680325200000000000L, 0.32, 0.0,  0.0);
            loadHourlyPoint(batchPoints, 1680328800000000000L, 0.40, 0.0,  0.0);
            loadHourlyPoint(batchPoints, 1680332400000000000L, 0.55, 0.0,  0.0);
            loadHourlyPoint(batchPoints, 1680336000000000000L, 0.68, 0.10, 0.15);
            loadHourlyPoint(batchPoints, 1680339600000000000L, 0.72, 0.20, 0.25);
            loadHourlyPoint(batchPoints, 1680343200000000000L, 0.75, 0.25, 0.30);
            loadHourlyPoint(batchPoints, 1680346800000000000L, 0.78, 0.30, 0.35);
            loadHourlyPoint(batchPoints, 1680350400000000000L, 0.80, 0.35, 0.40);
            loadHourlyPoint(batchPoints, 1680354000000000000L, 0.76, 0.28, 0.33);
            loadHourlyPoint(batchPoints, 1680357600000000000L, 0.70, 0.22, 0.27);
            loadHourlyPoint(batchPoints, 1680361200000000000L, 0.65, 0.15, 0.20);
            loadHourlyPoint(batchPoints, 1680364800000000000L, 0.58, 0.08, 0.12);
            loadHourlyPoint(batchPoints, 1680368400000000000L, 0.50, 0.0,  0.0);
            loadHourlyPoint(batchPoints, 1680372000000000000L, 0.55, 0.0,  0.0);
            loadHourlyPoint(batchPoints, 1680375600000000000L, 0.60, 0.0,  0.0);
            loadHourlyPoint(batchPoints, 1680379200000000000L, 0.58, 0.0,  0.0);
            loadHourlyPoint(batchPoints, 1680382800000000000L, 0.52, 0.0,  0.0);
            loadHourlyPoint(batchPoints, 1680386400000000000L, 0.48, 0.0,  0.0);
            loadHourlyPoint(batchPoints, 1680390000000000000L, 0.46, 0.0,  0.0);

            // April 2, 2023 - 6 hourly records
            loadHourlyPoint(batchPoints, 1680393600000000000L, 0.44, 0.0, 0.0);
            loadHourlyPoint(batchPoints, 1680397200000000000L, 0.40, 0.0, 0.0);
            loadHourlyPoint(batchPoints, 1680400800000000000L, 0.37, 0.0, 0.0);
            loadHourlyPoint(batchPoints, 1680404400000000000L, 0.36, 0.0, 0.0);
            loadHourlyPoint(batchPoints, 1680408000000000000L, 0.34, 0.0, 0.0);
            loadHourlyPoint(batchPoints, 1680411600000000000L, 0.35, 0.0, 0.0);

            connection.write(batchPoints);
        }
    }

    private void loadHourlyPoint(BatchPoints batchPoints, long timestampNanos,
                                  double consumption, double surplus, double selfConsumption) {
        loadHourlyPoint(batchPoints, CUPS_CODE, timestampNanos, consumption, surplus, selfConsumption);
    }

    private void loadHourlyPoint(BatchPoints batchPoints, String cups, long timestampNanos,
                                  double consumption, double surplus, double selfConsumption) {
        batchPoints.point(Point.measurement(DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT)
                .time(timestampNanos, TimeUnit.NANOSECONDS)
                .tag("cups", cups)
                .addField("consumption_kwh", consumption)
                .addField("surplus_energy_kwh", surplus)
                .addField("self_consumption_energy_kwh", selfConsumption)
                .addField("generation_energy_kwh", 0.0)
                .addField("obtain_method", "Real")
                .build());
    }

    private List<DatadisConsumptionMonthlyPoint> queryMonthlyData(String startDate, String endDate) {
        return queryMonthlyData(CUPS_CODE, startDate, endDate);
    }

    private List<DatadisConsumptionMonthlyPoint> queryMonthlyData(String cups, String startDate, String endDate) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            Query query = new Query(String.format(
                    "SELECT * FROM \"%s\" WHERE cups = '%s' AND time >= '%s' AND time <= '%s'",
                    DatadisConfigEntity.CONSUMPTION_KWH_MONTH_MEASUREMENT, cups, startDate, endDate));
            QueryResult result = connection.query(query);
            InfluxDBResultMapper mapper = new InfluxDBResultMapper();
            return mapper.toPOJO(result, DatadisConsumptionMonthlyPoint.class);
        }
    }

    /**
     * Records in the first and last local hour of January 2023 and in the adjoining local hours of
     * December 2022 and February 2023, all at Europe/Madrid's winter offset of UTC+1.
     */
    private void loadWinterBoundaryRecords() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();

            loadHourlyPointAt(batchPoints, CUPS_CODE, "2022-12-31T22:00:00Z", 100.0); // local 2022-12-31 23:00
            loadHourlyPointAt(batchPoints, CUPS_CODE, "2022-12-31T23:00:00Z", 1.0);   // local 2023-01-01 00:00
            loadHourlyPointAt(batchPoints, CUPS_CODE, "2023-01-15T12:00:00Z", 2.0);   // local 2023-01-15 13:00
            loadHourlyPointAt(batchPoints, CUPS_CODE, "2023-01-31T22:00:00Z", 4.0);   // local 2023-01-31 23:00
            loadHourlyPointAt(batchPoints, CUPS_CODE, "2023-01-31T23:00:00Z", 200.0); // local 2023-02-01 00:00

            connection.write(batchPoints);
        }
    }

    /**
     * Every hour of local January 2023 for both supplies -- 744 records each, January holding no DST
     * transition. Values are multiples of 0.25 kWh and differ between the two supplies.
     */
    private void loadFullLocalJanuary2023() {
        Instant firstHour = Instant.parse("2022-12-31T23:00:00Z"); // local 2023-01-01 00:00
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();

            for (int hour = 0; hour < 24 * 31; hour++) {
                Instant instant = firstHour.plusSeconds(3600L * hour);
                loadHourlyPointAt(batchPoints, CUPS_CODE, instant.toString(), 0.25 * (hour % 7 + 1));
                loadHourlyPointAt(batchPoints, SECOND_CUPS_CODE, instant.toString(), 0.25 * (hour % 5 + 1));
            }

            connection.write(batchPoints);
        }
    }

    private void loadHourlyPointAt(BatchPoints batchPoints, String cups, String utcInstant, double consumption) {
        batchPoints.point(Point.measurement(DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT)
                .time(Instant.parse(utcInstant).toEpochMilli(), TimeUnit.MILLISECONDS)
                .tag("cups", cups)
                .addField("consumption_kwh", consumption)
                .addField("surplus_energy_kwh", 0.0)
                .addField("self_consumption_energy_kwh", 0.0)
                .addField("generation_energy_kwh", 0.0)
                .addField("obtain_method", "Real")
                .build());
    }

    /**
     * Writes a monthly pre-aggregate directly, standing in for one left behind by an earlier run.
     */
    private void writeMonthlyPoint(String cups, String localMonthStart, double consumption) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();
            batchPoints.point(Point.measurement(DatadisConfigEntity.CONSUMPTION_KWH_MONTH_MEASUREMENT)
                    .time(OffsetDateTime.parse(localMonthStart).toInstant().toEpochMilli(), TimeUnit.MILLISECONDS)
                    .tag("cups", cups)
                    .addField("consumption_kwh", consumption)
                    .addField("surplus_energy_kwh", 0.0)
                    .addField("self_consumption_energy_kwh", 0.0)
                    .addField("generation_energy_kwh", 0.0)
                    .addField("obtain_method", "Real")
                    .build());
            connection.write(batchPoints);
        }
    }

    private double consumptionOfMonth(String localMonthStart) {
        return consumptionOfMonth(CUPS_CODE, localMonthStart);
    }

    /**
     * Reads back the single pre-aggregate stamped at the given local month start.
     */
    private double consumptionOfMonth(String cups, String localMonthStart) {
        Instant start = OffsetDateTime.parse(localMonthStart).toInstant();
        List<DatadisConsumptionMonthlyPoint> points = queryMonthlyData(cups,
                start.toString(), start.toString());

        assertEquals(1, points.size(), "Expected exactly one pre-aggregate at " + localMonthStart);
        return points.get(0).getConsumptionKWh();
    }

    private List<DatadisConsumptionMonthlyPoint> monthlyPointsOfJanuary2023(String cups) {
        return queryMonthlyData(cups, "2022-12-31T23:00:00Z", "2023-01-31T23:00:00Z");
    }
}
