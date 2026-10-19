package org.lucoenergia.conluz.infrastructure.production.huawei.aggregate;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.huawei.HourlyProduction;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionMonthlyAggregationService;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionYearlyAggregationService;
import org.lucoenergia.conluz.domain.production.huawei.config.GetHuaweiConfigurationService;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationRepository;
import org.lucoenergia.conluz.domain.production.huawei.persist.PersistHuaweiProductionRepository;
import org.lucoenergia.conluz.domain.production.huawei.sync.HuaweiSyncWindow;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The Huawei monthly and yearly aggregation jobs against real InfluxDB and PostgreSQL.
 *
 * <p>Two communities whose plants, two each, have an enabled Huawei configuration, and a third
 * community whose two plants have a disabled one. Huawei is configured per plant, so "a community
 * with Huawei disabled" is a community whose plants' configurations are all disabled. Every job is
 * built here from the application's own beans with a fixed {@link Clock}, rather than replacing the
 * application clock, which other components share.
 *
 * <p>Each hourly record carries the same value {@code v} scaled per field (see {@link #FIELDS}),
 * and every value is exactly representable as a double, so the expected sums are exact.
 */
@Transactional
class HuaweiPreAggregationJobsIntegrationTest extends BaseIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    private static final String HOUR = HuaweiConfig.HUAWEI_HOURLY_PRODUCTION_MEASUREMENT;
    private static final String MONTH = HuaweiConfig.HUAWEI_MONTHLY_PRODUCTION_MEASUREMENT;
    private static final String YEAR = HuaweiConfig.HUAWEI_YEARLY_PRODUCTION_MEASUREMENT;

    /** Each field of a record holds {@code v} times its factor. */
    private static final Map<String, Double> FIELDS = Map.of(
            "inverter_power", 1.0,
            "ongrid_power", 0.5,
            "power_profit", 0.25,
            "theory_power", 2.0,
            "radiation_intensity", 0.125);

    private static final double STALE = 999;

    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private SetHuaweiConfigurationRepository setHuaweiConfigurationRepository;
    @Autowired
    private PersistHuaweiProductionRepository persistHuaweiProductionRepository;
    @Autowired
    private InfluxDbConnectionManager influxDbConnectionManager;

    @Autowired
    private HuaweiProductionMonthlyAggregationService monthlyAggregationService;
    @Autowired
    private HuaweiProductionYearlyAggregationService yearlyAggregationService;
    @Autowired
    private GetHuaweiConfigurationService getHuaweiConfigurationService;
    @Autowired
    private HuaweiSyncWindow syncWindow;
    @Autowired
    private ZoneResolver zoneResolver;

    /** The plants of the two communities with Huawei enabled. */
    private final List<Plant> enabledPlants = new ArrayList<>();
    /** The plants of the community with Huawei disabled. */
    private final List<Plant> disabledPlants = new ArrayList<>();

    @BeforeEach
    void persistCommunities() {
        for (int i = 0; i < 2; i++) {
            Community community = createCommunityRepository.create(CommunityMother.random().build());
            enabledPlants.add(persistPlant(community, true));
            enabledPlants.add(persistPlant(community, true));
        }
        Community disabled = createCommunityRepository.create(CommunityMother.random().build());
        disabledPlants.add(persistPlant(disabled, false));
        disabledPlants.add(persistPlant(disabled, false));
    }

    @AfterEach
    void clearWrittenSeries() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            for (Plant plant : allPlants()) {
                for (String measurement : List.of(HOUR, MONTH, YEAR)) {
                    connection.query(new Query(String.format("DROP SERIES FROM \"%s\" WHERE \"station_code\" = '%s'",
                            measurement, plant.getProviderCode())));
                }
            }
        }
    }

    /**
     * AC1, AC2 and AC4. On 1 November, October's last day and its 25-hour DST day (2026-10-25) have
     * been synced. October's point, stale since its last run, is rewritten to the full local month,
     * for every plant of both enabled communities; the hour before and the hour after the local
     * month are not counted.
     */
    @Test
    @DisplayName("HPA-003 on the 1st, the closed month is rewritten to the sum of its full local month, including its last day and its 25-hour DST day, for every enabled plant")
    void onTheFirstTheClosedMonthIsRewrittenToItsFullLocalMonth() {
        double factor = 1;
        Map<Plant, Double> expected = new HashMap<>();
        for (Plant plant : allPlants()) {
            writeMonth(plant, YearMonth.of(2026, 10), STALE);
            writeHour(plant, startOfDay(LocalDate.of(2026, 10, 1)).minusSeconds(3600), 1000 * factor); // September
            writeHour(plant, startOfDay(LocalDate.of(2026, 10, 1)), 0.5 * factor);
            assertEquals(25, writeEveryHourOf(plant, LocalDate.of(2026, 10, 25), factor));
            assertEquals(24, writeEveryHourOf(plant, LocalDate.of(2026, 10, 31), 2 * factor));
            writeHour(plant, startOfDay(LocalDate.of(2026, 11, 1)), 1000 * factor); // November
            expected.put(plant, (0.5 + 25 + 48) * factor);
            factor += 1;
        }

        monthlyJob(clockAt("2026-11-01T01:00:00Z")).run(); // 02:00 in Madrid

        for (Plant plant : enabledPlants) {
            assertPoint(MONTH, plant, YearMonth.of(2026, 10), expected.get(plant));
        }
        for (Plant plant : disabledPlants) {
            assertPoint(MONTH, plant, YearMonth.of(2026, 10), STALE);
        }
    }

    /**
     * AC2. March 2026 has a 23-hour DST day (2026-03-29) that is not its last day: the run on
     * 1 April counts every hour of it and every hour of 31 March.
     */
    @Test
    @DisplayName("HPA-003 a month with a 23-hour DST day is rewritten with every hour of that day and of its last day")
    void aMonthWithAShortDstDayIsRewrittenWithEveryHourOfIt() {
        for (Plant plant : enabledPlants) {
            writeMonth(plant, YearMonth.of(2026, 3), STALE);
            assertEquals(23, writeEveryHourOf(plant, LocalDate.of(2026, 3, 29), 1));
            assertEquals(24, writeEveryHourOf(plant, LocalDate.of(2026, 3, 31), 2));
            writeHour(plant, startOfDay(LocalDate.of(2026, 4, 1)), 1000); // April
        }

        monthlyJob(clockAt("2026-04-01T00:00:00Z")).run(); // 02:00 in Madrid (CEST)

        for (Plant plant : enabledPlants) {
            assertPoint(MONTH, plant, YearMonth.of(2026, 3), 23 + 48);
        }
    }

    /**
     * AC3 and AC7. A run on 3 October covers 26 September onwards: both September and October are
     * rewritten, while August and the year 2025, outside the window, keep their stored values even
     * though their records changed.
     */
    @Test
    @DisplayName("HPA-003 a run on the 3rd rewrites the previous and the current month, and leaves periods outside the window alone")
    void aRunOnTheThirdRewritesBothMonthsAndNothingOutsideTheWindow() {
        for (Plant plant : allPlants()) {
            writeMonth(plant, YearMonth.of(2025, 12), 7);
            writeYear(plant, 2025, 777);
            writeMonth(plant, YearMonth.of(2026, 8), 777);
            writeHour(plant, startOfDay(LocalDate.of(2026, 8, 15)), 100);
            writeMonth(plant, YearMonth.of(2026, 9), STALE);
            writeEveryHourOf(plant, LocalDate.of(2026, 9, 30), 1);
            writeMonth(plant, YearMonth.of(2026, 10), STALE);
            writeEveryHourOf(plant, LocalDate.of(2026, 10, 2), 0.5);
            writeYear(plant, 2026, STALE);
        }

        monthlyJob(clockAt("2026-10-03T00:00:00Z")).run(); // 02:00 in Madrid
        yearlyJob(clockAt("2026-10-03T01:00:00Z")).run(); // 03:00 in Madrid

        for (Plant plant : enabledPlants) {
            assertPoint(MONTH, plant, YearMonth.of(2026, 9), 24);
            assertPoint(MONTH, plant, YearMonth.of(2026, 10), 12);
            assertPoint(MONTH, plant, YearMonth.of(2026, 8), 777);
            assertYear(plant, 2026, 777 + 24 + 12);
            assertYear(plant, 2025, 777);
        }
        for (Plant plant : disabledPlants) {
            assertPoint(MONTH, plant, YearMonth.of(2026, 9), STALE);
            assertPoint(MONTH, plant, YearMonth.of(2026, 10), STALE);
            assertYear(plant, 2026, STALE);
        }
    }

    /**
     * AC6. On 1 January, the monthly run rewrites December with 31 December, and the yearly run
     * then rewrites the previous year's total from it.
     */
    @Test
    @DisplayName("HPA-003 on 1 January, the previous year's total includes the whole of 31 December")
    void onTheFirstOfJanuaryThePreviousYearIncludesTheLastDay() {
        for (Plant plant : allPlants()) {
            writeMonth(plant, YearMonth.of(2026, 6), 10);
            writeMonth(plant, YearMonth.of(2026, 12), STALE);
            writeEveryHourOf(plant, LocalDate.of(2026, 12, 1), 1);
            assertEquals(24, writeEveryHourOf(plant, LocalDate.of(2026, 12, 31), 2));
            writeYear(plant, 2026, STALE);
        }

        monthlyJob(clockAt("2027-01-01T01:00:00Z")).run(); // 02:00 in Madrid
        yearlyJob(clockAt("2027-01-01T02:00:00Z")).run(); // 03:00 in Madrid

        for (Plant plant : enabledPlants) {
            assertPoint(MONTH, plant, YearMonth.of(2026, 12), 24 + 48);
            assertYear(plant, 2026, 10 + 24 + 48);
        }
        for (Plant plant : disabledPlants) {
            assertYear(plant, 2026, STALE);
        }
    }

    // --- Jobs, built from the application beans with a fixed clock ---

    private HuaweiProductionMonthlyAggregationJob monthlyJob(Clock clock) {
        return new HuaweiProductionMonthlyAggregationJob(monthlyAggregationService, clock,
                getHuaweiConfigurationService, syncWindow, zoneResolver);
    }

    private HuaweiProductionYearlyAggregationJob yearlyJob(Clock clock) {
        return new HuaweiProductionYearlyAggregationJob(yearlyAggregationService, clock,
                getHuaweiConfigurationService, syncWindow, zoneResolver);
    }

    private static Clock clockAt(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    // --- PostgreSQL ---

    private Plant persistPlant(Community community, boolean huaweiEnabled) {
        User owner = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(SupplyMother.random(owner).build(), UserId.of(owner.getId()),
                community.getId());
        Plant plant = createPlantRepository.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId()));
        setHuaweiConfigurationRepository.setHuaweiConfiguration(plant.getId(), new HuaweiConfig.Builder()
                .setUsername("user").setPassword("password").setBaseUrl(HuaweiConfig.DEFAULT_BASE_URL)
                .setEnabled(huaweiEnabled).build());
        return plant;
    }

    private List<Plant> allPlants() {
        List<Plant> plants = new ArrayList<>(enabledPlants);
        plants.addAll(disabledPlants);
        return plants;
    }

    // --- InfluxDB ---

    private static Instant startOfDay(LocalDate day) {
        return day.atStartOfDay(ZONE).toInstant();
    }

    private static Instant start(YearMonth month) {
        return startOfDay(month.atDay(1));
    }

    /**
     * Writes one hourly record for every hour of the local day, through the production
     * persistence, and returns how many hours the day has.
     */
    private int writeEveryHourOf(Plant plant, LocalDate day, double value) {
        List<HourlyProduction> records = new ArrayList<>();
        for (Instant hour = startOfDay(day); hour.isBefore(startOfDay(day.plusDays(1))); hour = hour.plusSeconds(3600)) {
            records.add(hourlyRecord(plant, hour, value));
        }
        persistHuaweiProductionRepository.persistHourlyProduction(records);
        return records.size();
    }

    private void writeHour(Plant plant, Instant hour, double value) {
        persistHuaweiProductionRepository.persistHourlyProduction(List.of(hourlyRecord(plant, hour, value)));
    }

    private static HourlyProduction hourlyRecord(Plant plant, Instant hour, double value) {
        return new HourlyProduction.Builder()
                .withTime(hour)
                .withStationCode(plant.getProviderCode())
                .withInverterPower(value * FIELDS.get("inverter_power"))
                .withOngridPower(value * FIELDS.get("ongrid_power"))
                .withPowerProfit(value * FIELDS.get("power_profit"))
                .withTheoryPower(value * FIELDS.get("theory_power"))
                .withRadiationIntensity(value * FIELDS.get("radiation_intensity"))
                .build();
    }

    private void writeMonth(Plant plant, YearMonth month, double value) {
        writeAggregate(MONTH, plant, start(month), value);
    }

    private void writeYear(Plant plant, int year, double value) {
        writeAggregate(YEAR, plant, start(YearMonth.of(year, 1)), value);
    }

    /**
     * Writes a pre-aggregate point directly: the only production writer of these measurements is
     * the aggregation under test, which cannot produce a stale value.
     */
    private void writeAggregate(String measurement, Plant plant, Instant time, double value) {
        Point.Builder point = Point.measurement(measurement)
                .time(time.toEpochMilli(), TimeUnit.MILLISECONDS)
                .tag("station_code", plant.getProviderCode());
        FIELDS.forEach((field, factor) -> point.addField(field, value * factor));
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            BatchPoints batch = influxDbConnectionManager.createBatchPoints();
            batch.point(point.build());
            connection.write(batch);
        }
    }

    private void assertPoint(String measurement, Plant plant, YearMonth month, double expected) {
        Map<String, Object> point = read(measurement, plant, start(month));
        FIELDS.forEach((field, factor) -> assertEquals(expected * factor, (Double) point.get(field),
                field + " of " + measurement + " " + month + " for " + plant.getProviderCode()));
    }

    private void assertYear(Plant plant, int year, double expected) {
        assertPoint(YEAR, plant, YearMonth.of(year, 1), expected);
    }

    private Map<String, Object> read(String measurement, Plant plant, Instant time) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            QueryResult result = connection.query(new Query(String.format(
                    "SELECT * FROM \"%s\" WHERE \"station_code\" = '%s' AND time = '%s'",
                    measurement, plant.getProviderCode(), time)));
            QueryResult.Series series = result.getResults().get(0).getSeries().get(0);
            assertEquals(1, series.getValues().size(), "exactly one point at " + time);
            Map<String, Object> point = new HashMap<>();
            for (int i = 0; i < series.getColumns().size(); i++) {
                point.put(series.getColumns().get(i), series.getValues().get(0).get(i));
            }
            return point;
        }
    }
}
