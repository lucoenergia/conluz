package org.lucoenergia.conluz.infrastructure.datadis;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisMonthlyAggregationService;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisYearlyAggregationService;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.GetDatadisConfigurationService;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationRepository;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncWindow;
import org.lucoenergia.conluz.domain.production.datadis.aggregate.DatadisProductionMonthlyAggregationService;
import org.lucoenergia.conluz.domain.production.datadis.aggregate.DatadisProductionYearlyAggregationService;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.datadis.aggregate.DatadisMonthlyAggregationJob;
import org.lucoenergia.conluz.infrastructure.datadis.aggregate.DatadisYearlyAggregationJob;
import org.lucoenergia.conluz.infrastructure.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.production.datadis.DatadisProductionMeasurements;
import org.lucoenergia.conluz.infrastructure.production.datadis.aggregate.DatadisProductionMonthlyAggregationJob;
import org.lucoenergia.conluz.infrastructure.production.datadis.aggregate.DatadisProductionYearlyAggregationJob;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The four scheduled Datadis pre-aggregation jobs, end to end over a real PostgreSQL and InfluxDB.
 *
 * <p>Two communities with Datadis enabled, two supplies each, and a third community whose Datadis
 * config is disabled. Every job is built here from the application's own beans with a fixed
 * {@link Clock}, rather than replacing the application clock, which other components share.
 *
 * <p>The jobs run on 2026-10-09 at 05:00 in Madrid, so the sync window is 2025-10 to 2026-10 and
 * the years it overlaps are 2025 and 2026. Every energy value is exactly representable as a float,
 * so the expected sums are exact.
 */
@Transactional
class DatadisPreAggregationJobsIntegrationTest extends BaseIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-09T03:00:00Z"), ZoneOffset.UTC);

    private static final YearMonth BEFORE_WINDOW = YearMonth.of(2025, 9);
    private static final YearMonth FIRST_MONTH = YearMonth.of(2025, 10);
    private static final YearMonth CLOSED_MONTH = YearMonth.of(2026, 5);
    private static final YearMonth CURRENT_MONTH = YearMonth.of(2026, 10);

    private static final String CONSUMPTION_MONTH = DatadisConfigEntity.CONSUMPTION_KWH_MONTH_MEASUREMENT;
    private static final String CONSUMPTION_YEAR = DatadisConfigEntity.CONSUMPTION_KWH_YEAR_MEASUREMENT;
    private static final String PRODUCTION_HOUR = DatadisProductionMeasurements.PRODUCTION_KWH_MEASUREMENT;
    private static final String PRODUCTION_MONTH = DatadisProductionMeasurements.PRODUCTION_KWH_MONTH_MEASUREMENT;
    private static final String PRODUCTION_YEAR = DatadisProductionMeasurements.PRODUCTION_KWH_YEAR_MEASUREMENT;

    private static final List<String> CONSUMPTION_FIELDS = List.of(
            "consumption_kwh", "self_consumption_energy_kwh", "surplus_energy_kwh", "generation_energy_kwh");

    @Autowired
    private CommunityJpaRepository communityJpaRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SupplyRepository supplyRepository;
    @Autowired
    private SetDatadisConfigurationRepository setDatadisConfigurationRepository;
    @Autowired
    private DatadisConsumptionInfluxFixture influxFixture;
    @Autowired
    private InfluxDbConnectionManager influxDbConnectionManager;

    @Autowired
    private DatadisMonthlyAggregationService monthlyAggregationService;
    @Autowired
    private DatadisProductionMonthlyAggregationService productionMonthlyAggregationService;
    @Autowired
    private DatadisYearlyAggregationService yearlyAggregationService;
    @Autowired
    private DatadisProductionYearlyAggregationService productionYearlyAggregationService;
    @Autowired
    private GetDatadisConfigurationService getDatadisConfigurationService;
    @Autowired
    private DatadisSyncWindow syncWindow;
    @Autowired
    private ZoneResolver zoneResolver;

    /** The supplies of the two communities with Datadis enabled. */
    private final List<SupplyEntity> enabledSupplies = new ArrayList<>();
    private SupplyEntity disabledSupply;

    @BeforeEach
    void persistCommunities() {
        for (int i = 0; i < 2; i++) {
            CommunityEntity community = persistCommunity(true);
            enabledSupplies.add(persistSupply(community));
            enabledSupplies.add(persistSupply(community));
        }
        disabledSupply = persistSupply(persistCommunity(false));
    }

    @AfterEach
    void clearWrittenSeries() {
        List<SupplyEntity> supplies = new ArrayList<>(enabledSupplies);
        supplies.add(disabledSupply);
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            for (SupplyEntity supply : supplies) {
                for (String measurement : List.of(DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT, CONSUMPTION_MONTH,
                        CONSUMPTION_YEAR, PRODUCTION_HOUR, PRODUCTION_MONTH, PRODUCTION_YEAR)) {
                    connection.query(new Query(String.format("DROP SERIES FROM \"%s\" WHERE \"cups\" = '%s'",
                            measurement, supply.getCode())));
                }
            }
        }
    }

    // --- DCA-001: monthly consumption ---

    /**
     * AC1. May 2026 was aggregated with preliminary data: no self-consumption, no generation and an
     * outdated grid import. Its hourly records have since been revised, including the month's first
     * and last local hour.
     */
    @Test
    @DisplayName("DCA-001 a stale point of a closed month in the window is rewritten to the sum of its hourly records, per field")
    void aStalePointOfAClosedMonthIsRewrittenToTheSumOfItsHourlyRecords() {
        SupplyEntity supply = enabledSupplies.get(0);
        writeConsumptionMonth(supply, CLOSED_MONTH, 90f, 0f, 3f, 0f);
        writeHour(supply, "2026/05/01", "00:00", 10.5f, 4.25f, 1f, 6f);
        writeHour(supply, "2026/05/15", "13:00", 20f, 8f, 2.5f, 12f);
        writeHour(supply, "2026/05/31", "23:00", 1.25f, 0.5f, 0.25f, 1f);
        writeHour(supply, "2026/06/01", "00:00", 1000f, 1000f, 1000f, 1000f); // June, not May

        monthlyConsumptionJob().run();

        Map<String, Object> point = read(CONSUMPTION_MONTH, supply, start(CLOSED_MONTH));
        assertEquals(31.75, (Double) point.get("consumption_kwh"));
        assertEquals(12.75, (Double) point.get("self_consumption_energy_kwh"));
        assertEquals(3.75, (Double) point.get("surplus_energy_kwh"));
        assertEquals(19.0, (Double) point.get("generation_energy_kwh"));
    }

    /**
     * AC2 and AC4. Every supply of both enabled communities holds a stale point in the first month
     * of the window, a closed month and the current month. A single run corrects all of them.
     */
    @Test
    @DisplayName("DCA-001 a single run corrects every month of the window for every supply of every Datadis-enabled community")
    void aSingleRunCorrectsEveryMonthOfTheWindowForEverySupply() {
        Map<String, double[]> expected = new HashMap<>();
        float base = 1f;
        for (SupplyEntity supply : enabledSupplies) {
            for (YearMonth month : List.of(FIRST_MONTH, CLOSED_MONTH, CURRENT_MONTH)) {
                writeConsumptionMonth(supply, month, 999f, 999f, 999f, 999f);
                String day = String.format("%d/%02d/01", month.getYear(), month.getMonthValue());
                writeHour(supply, day, "00:00", base, base / 2, base / 4, base * 2);
                writeHour(supply, day, "12:00", base * 3, base, base / 2, base);
                expected.put(supply.getCode() + month, new double[]{base * 4, base * 1.5, base * 0.75, base * 3});
                base += 1f;
            }
        }

        monthlyConsumptionJob().run();

        for (SupplyEntity supply : enabledSupplies) {
            for (YearMonth month : List.of(FIRST_MONTH, CLOSED_MONTH, CURRENT_MONTH)) {
                Map<String, Object> point = read(CONSUMPTION_MONTH, supply, start(month));
                double[] sums = expected.get(supply.getCode() + month);
                for (int i = 0; i < CONSUMPTION_FIELDS.size(); i++) {
                    assertEquals(sums[i], (Double) point.get(CONSUMPTION_FIELDS.get(i)),
                            CONSUMPTION_FIELDS.get(i) + " of " + month);
                }
            }
        }
    }

    /**
     * AC3. September 2025 is the month before the window: its hourly records changed too, but its
     * point keeps the value it had.
     */
    @Test
    @DisplayName("DCA-001 a month before the window is not rewritten")
    void aMonthBeforeTheWindowIsNotRewritten() {
        SupplyEntity supply = enabledSupplies.get(1);
        writeConsumptionMonth(supply, BEFORE_WINDOW, 7f, 6f, 5f, 4f);
        writeHour(supply, "2025/09/15", "12:00", 100f, 100f, 100f, 100f);

        monthlyConsumptionJob().run();

        Map<String, Object> point = read(CONSUMPTION_MONTH, supply, start(BEFORE_WINDOW));
        assertEquals(7.0, (Double) point.get("consumption_kwh"));
        assertEquals(6.0, (Double) point.get("self_consumption_energy_kwh"));
        assertEquals(5.0, (Double) point.get("surplus_energy_kwh"));
        assertEquals(4.0, (Double) point.get("generation_energy_kwh"));
    }

    @Test
    @DisplayName("DCA-001 a community with Datadis disabled is not touched")
    void aCommunityWithDatadisDisabledIsNotTouched() {
        writeConsumptionMonth(disabledSupply, CLOSED_MONTH, 7f, 6f, 5f, 4f);
        writeHour(disabledSupply, "2026/05/15", "12:00", 100f, 100f, 100f, 100f);

        monthlyConsumptionJob().run();

        Map<String, Object> point = read(CONSUMPTION_MONTH, disabledSupply, start(CLOSED_MONTH));
        assertEquals(7.0, (Double) point.get("consumption_kwh"));
        assertEquals(6.0, (Double) point.get("self_consumption_energy_kwh"));
    }

    // --- DCA-002: monthly production ---

    @Test
    @DisplayName("DCA-002 a single run rewrites every month of the window to the sum of its hourly production, for every supply of every Datadis-enabled community")
    void aSingleRunRewritesEveryMonthOfTheWindowToTheSumOfItsHourlyProduction() {
        float base = 1f;
        Map<String, Double> expected = new HashMap<>();
        for (SupplyEntity supply : enabledSupplies) {
            for (YearMonth month : List.of(FIRST_MONTH, CLOSED_MONTH, CURRENT_MONTH)) {
                writeProduction(PRODUCTION_MONTH, supply, start(month), 999f);
                // The month's first local hour, one in the middle, and the next month's first local hour.
                writeProduction(PRODUCTION_HOUR, supply, start(month), base);
                writeProduction(PRODUCTION_HOUR, supply, start(month).plusSeconds(10 * 86_400), base * 2);
                writeProduction(PRODUCTION_HOUR, supply, start(month.plusMonths(1)), 1000f);
                expected.put(supply.getCode() + month, (double) base * 3);
                base += 1f;
            }
        }

        productionMonthlyJob().run();

        for (SupplyEntity supply : enabledSupplies) {
            for (YearMonth month : List.of(FIRST_MONTH, CLOSED_MONTH, CURRENT_MONTH)) {
                assertEquals(expected.get(supply.getCode() + month),
                        (Double) read(PRODUCTION_MONTH, supply, start(month)).get("production_kwh"),
                        "production of " + month);
            }
        }
    }

    @Test
    @DisplayName("DCA-002 a month before the window, and a community with Datadis disabled, are not rewritten")
    void aMonthBeforeTheWindowAndADisabledCommunityAreNotRewritten() {
        SupplyEntity supply = enabledSupplies.get(2);
        writeProduction(PRODUCTION_MONTH, supply, start(BEFORE_WINDOW), 7f);
        writeProduction(PRODUCTION_HOUR, supply, start(BEFORE_WINDOW).plusSeconds(86_400), 100f);
        writeProduction(PRODUCTION_MONTH, disabledSupply, start(CLOSED_MONTH), 7f);
        writeProduction(PRODUCTION_HOUR, disabledSupply, start(CLOSED_MONTH).plusSeconds(86_400), 100f);

        productionMonthlyJob().run();

        assertEquals(7.0, (Double) read(PRODUCTION_MONTH, supply, start(BEFORE_WINDOW)).get("production_kwh"));
        assertEquals(7.0, (Double) read(PRODUCTION_MONTH, disabledSupply, start(CLOSED_MONTH)).get("production_kwh"));
    }

    // --- DCA-003: yearly consumption and production ---

    /**
     * Monthly points stamped as the monthly jobs stamp them, at local midnight of the 1st: January
     * of a year sits at 23:00Z of the previous one.
     */
    @Test
    @DisplayName("DCA-003 a single run rewrites every year the window overlaps to the sum of its monthly points, and leaves earlier years alone")
    void aSingleRunRewritesEveryYearTheWindowOverlapsToTheSumOfItsMonthlyPoints() {
        float base = 1f;
        for (SupplyEntity supply : enabledSupplies) {
            seedYears(supply, base);
            base += 1f;
        }
        seedYears(disabledSupply, 50f);

        yearlyConsumptionJob().run();
        productionYearlyJob().run();

        base = 1f;
        for (SupplyEntity supply : enabledSupplies) {
            // 2025: January (base) + December (2 * base); 2026: January (3 * base) + September (4 * base).
            assertYear(supply, 2025, base * 3);
            assertYear(supply, 2026, base * 7);
            assertYear(supply, 2024, 999);
            base += 1f;
        }
        assertYear(disabledSupply, 2025, 999);
        assertYear(disabledSupply, 2026, 999);
    }

    private void seedYears(SupplyEntity supply, float base) {
        for (int year : List.of(2024, 2025, 2026)) {
            writeConsumptionYear(supply, year, 999f);
            writeProduction(PRODUCTION_YEAR, supply, start(YearMonth.of(year, 1)), 999f);
        }
        writeMonthlyOfEveryKind(supply, YearMonth.of(2024, 12), 500f);
        writeMonthlyOfEveryKind(supply, YearMonth.of(2025, 1), base);
        writeMonthlyOfEveryKind(supply, YearMonth.of(2025, 12), base * 2);
        writeMonthlyOfEveryKind(supply, YearMonth.of(2026, 1), base * 3);
        writeMonthlyOfEveryKind(supply, YearMonth.of(2026, 9), base * 4);
    }

    private void writeMonthlyOfEveryKind(SupplyEntity supply, YearMonth month, float value) {
        writeConsumptionMonth(supply, month, value, value, value, value);
        writeProduction(PRODUCTION_MONTH, supply, start(month), value);
    }

    private void assertYear(SupplyEntity supply, int year, double expected) {
        Instant start = start(YearMonth.of(year, 1));
        Map<String, Object> consumption = read(CONSUMPTION_YEAR, supply, start);
        for (String field : CONSUMPTION_FIELDS) {
            assertEquals(expected, (Double) consumption.get(field), field + " of " + year);
        }
        assertEquals(expected, (Double) read(PRODUCTION_YEAR, supply, start).get("production_kwh"),
                "production of " + year);
    }

    // --- Jobs, built from the application beans with a fixed clock ---

    private DatadisMonthlyAggregationJob monthlyConsumptionJob() {
        return new DatadisMonthlyAggregationJob(monthlyAggregationService, CLOCK, getDatadisConfigurationService,
                syncWindow, zoneResolver);
    }

    private DatadisProductionMonthlyAggregationJob productionMonthlyJob() {
        return new DatadisProductionMonthlyAggregationJob(productionMonthlyAggregationService, CLOCK,
                getDatadisConfigurationService, syncWindow, zoneResolver);
    }

    private DatadisYearlyAggregationJob yearlyConsumptionJob() {
        return new DatadisYearlyAggregationJob(yearlyAggregationService, CLOCK, getDatadisConfigurationService,
                syncWindow, zoneResolver);
    }

    private DatadisProductionYearlyAggregationJob productionYearlyJob() {
        return new DatadisProductionYearlyAggregationJob(productionYearlyAggregationService, CLOCK,
                getDatadisConfigurationService, syncWindow, zoneResolver);
    }

    // --- PostgreSQL ---

    private CommunityEntity persistCommunity(boolean datadisEnabled) {
        CommunityEntity community = communityJpaRepository.save(CommunityMother.randomEntity().build());
        setDatadisConfigurationRepository.setDatadisConfiguration(community.getId(), new DatadisConfig.Builder()
                .setUsername("user").setPassword("password").setEnabled(datadisEnabled).build());
        return community;
    }

    private SupplyEntity persistSupply(CommunityEntity community) {
        User owner = UserMother.randomUser();
        owner.enable();
        createUserRepository.create(owner);
        return supplyRepository.save(SupplyEntityMother.random(
                userRepository.getReferenceById(owner.getId()), community));
    }

    // --- InfluxDB ---

    private static Instant start(YearMonth month) {
        return month.atDay(1).atStartOfDay(ZONE).toInstant();
    }

    /**
     * Writes one hourly consumption record through the production persistence, at a local date and
     * time.
     */
    private void writeHour(SupplyEntity supply, String date, String time, float consumption, float selfConsumption,
                           float surplus, float generation) {
        DatadisConsumption record = DatadisConsumptionInfluxFixture.hourlyRecord(supply.getCode(), date, time,
                consumption, selfConsumption, surplus);
        record.setGenerationEnergyKWh(generation);
        influxFixture.write(List.of(record));
    }

    private void writeConsumptionMonth(SupplyEntity supply, YearMonth month, float consumption,
                                       float selfConsumption, float surplus, float generation) {
        writeConsumptionPoint(CONSUMPTION_MONTH, supply, start(month), consumption, selfConsumption, surplus,
                generation);
    }

    private void writeConsumptionYear(SupplyEntity supply, int year, float value) {
        writeConsumptionPoint(CONSUMPTION_YEAR, supply, start(YearMonth.of(year, 1)), value, value, value, value);
    }

    private void writeConsumptionPoint(String measurement, SupplyEntity supply, Instant time, float consumption,
                                       float selfConsumption, float surplus, float generation) {
        write(Point.measurement(measurement)
                .time(time.toEpochMilli(), TimeUnit.MILLISECONDS)
                .tag("cups", supply.getCode())
                .addField("consumption_kwh", (double) consumption)
                .addField("self_consumption_energy_kwh", (double) selfConsumption)
                .addField("surplus_energy_kwh", (double) surplus)
                .addField("generation_energy_kwh", (double) generation)
                .addField("obtain_method", "Real")
                .build());
    }

    private void writeProduction(String measurement, SupplyEntity supply, Instant time, float production) {
        write(Point.measurement(measurement)
                .time(time.toEpochMilli(), TimeUnit.MILLISECONDS)
                .tag("cups", supply.getCode())
                .addField("production_kwh", (double) production)
                .addField("obtain_method", "Real")
                .build());
    }

    private void write(Point point) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();
            batchPoints.point(point);
            connection.write(batchPoints);
        }
    }

    /**
     * The fields of the single point the supply has at that instant in the measurement.
     */
    private Map<String, Object> read(String measurement, SupplyEntity supply, Instant time) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            QueryResult result = connection.query(new Query(String.format(
                    "SELECT * FROM \"%s\" WHERE cups = '%s' AND time = '%s'", measurement, supply.getCode(), time)));
            List<QueryResult.Series> series = result.getResults().get(0).getSeries();
            assertNotNull(series, "no " + measurement + " point at " + time + " (" + LocalDateTime.ofInstant(time, ZONE)
                    + " local)");
            assertEquals(1, series.get(0).getValues().size(), "points at " + time);
            Map<String, Object> fields = new HashMap<>();
            List<String> columns = series.get(0).getColumns();
            for (int i = 0; i < columns.size(); i++) {
                fields.put(columns.get(i), series.get(0).getValues().get(0).get(i));
            }
            return fields;
        }
    }
}
