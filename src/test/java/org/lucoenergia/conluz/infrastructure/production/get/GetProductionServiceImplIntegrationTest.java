package org.lucoenergia.conluz.infrastructure.production.get;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.InstantProduction;
import org.lucoenergia.conluz.domain.production.ProductionByTime;
import org.lucoenergia.conluz.domain.production.get.GetProductionService;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.production.EnergyProductionInfluxLoader;
import org.lucoenergia.conluz.infrastructure.production.ProductionPoint;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.SharingAgreementStatus;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;

/**
 * End-to-end tests for the CoefficientResolver-driven per-supply production path, against real
 * InfluxDB + Postgres (Testcontainers, via {@link BaseIntegrationTest}). Covers the epic-required
 * cases that a mocked-repository unit test ({@link GetProductionServiceTest}) cannot: a real
 * coefficient timeline read from Postgres, real raw production read from InfluxDB, and the
 * community-total non-regression guard.
 * <p>
 * NOTE ON WHAT THIS FILE USED TO TEST: before this phase, per-supply production was scoped to "all
 * plants in the supply's own community" ({@code resolveStationCodes}), and this file pinned that a
 * supply could not see another community's production. This phase replaces that mechanism entirely:
 * the plant set is now derived solely from {@code supply_partition_coefficient} (see the
 * AUTHORIZATION-SURFACE NOTE in {@link GetProductionServiceImpl}), so a supply's production now
 * follows wherever its coefficient rows point, regardless of community. The old containment test is
 * gone because its premise no longer holds; {@link #hourlyProductionFollowsTheCoefficientRowsPlantRegardlessOfCommunity()}
 * pins the new, intentional behaviour instead, specifically so nobody "fixes" this back later.
 */
@SpringBootTest
@Transactional
class GetProductionServiceImplIntegrationTest extends BaseIntegrationTest {

    private static final String START_DATE = "2023-09-01T00:00:00.000+02:00";
    private static final String END_DATE = "2023-09-01T23:00:00.000+02:00";
    private static final String OTHER_STATION_CODE = "PLANT002";
    // Peak seeded hour (31.1 kW at PLANT001) -- see EnergyProductionInfluxLoader.
    private static final Instant PEAK_HOUR = Instant.parse("2023-09-01T12:00:00Z");

    @Autowired
    private GetProductionService service;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private PlantRepository plantRepository;
    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;
    @Autowired
    private SupplyPartitionCoefficientRepository supplyPartitionCoefficientRepository;
    @Autowired
    private EnergyProductionInfluxLoader energyProductionInfluxLoader;
    @Autowired
    private InfluxDbConnectionManager influxDbConnectionManager;

    private Plant plant;

    @BeforeEach
    void beforeEach() {
        energyProductionInfluxLoader.loadData();
        // Registered in the default community, but that no longer matters for per-supply resolution
        // (see the class Javadoc) -- only the coefficient rows created per-test do.
        User plantOwner = createUserRepository.create(UserMother.randomUser());
        Supply plantSupply = createSupplyRepository.create(SupplyMother.random().build(), UserId.of(plantOwner.getId()));
        plant = createPlantRepository.create(
                PlantMother.random(plantSupply).withProviderCode(EnergyProductionInfluxLoader.STATION_CODE).build(),
                SupplyId.of(plantSupply.getId()));
    }

    @AfterEach
    void afterEach() {
        energyProductionInfluxLoader.clearData();
    }

    private Supply createTestedSupply() {
        User user = createUserRepository.create(UserMother.randomUser());
        return createSupplyRepository.create(SupplyMother.random(user).build(), UserId.of(user.getId()));
    }

    private void persistCoefficient(UUID supplyId, Plant plant, BigDecimal coefficient, Instant validFrom, Instant validTo) {
        SharingAgreementEntity agreement = new SharingAgreementEntity();
        agreement.setId(UUID.randomUUID());
        agreement.setPlant(plantRepository.getReferenceById(plant.getId()));
        agreement.setName("Test agreement " + UUID.randomUUID());
        agreement.setStatus(SharingAgreementStatus.PUBLISHED);
        agreement.setCreatedAt(Instant.now());
        agreement.setCreatedBy(null);
        sharingAgreementRepository.save(agreement);

        supplyPartitionCoefficientRepository.save(new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supplyId)
                .withPlantId(plant.getId())
                .withSharingAgreementId(agreement.getId())
                .withCoefficient(coefficient)
                .withValidFrom(validFrom)
                .withValidTo(validTo)
                .withCreatedAt(Instant.now())
                .build());
    }

    /** Seeds a second station's raw hourly production, independent of the shared loader's fixture. */
    private Plant createSecondPlant(double powerAtEveryHour) {
        User owner = createUserRepository.create(UserMother.randomUser());
        Supply ownerSupply = createSupplyRepository.create(SupplyMother.random().build(), UserId.of(owner.getId()));
        Plant secondPlant = createPlantRepository.create(
                PlantMother.random(ownerSupply).withProviderCode(OTHER_STATION_CODE).build(),
                SupplyId.of(ownerSupply.getId()));

        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();
            for (long hour = 0; hour < 24; hour++) {
                batchPoints.point(Point.measurement(HuaweiConfig.HUAWEI_HOURLY_PRODUCTION_MEASUREMENT)
                        .time(Instant.parse("2023-08-31T22:00:00Z").plusSeconds(hour * 3600).toEpochMilli(), TimeUnit.MILLISECONDS)
                        .tag("station_code", OTHER_STATION_CODE)
                        .addField(ProductionPoint.INVERTER_POWER, powerAtEveryHour)
                        .build());
            }
            connection.write(batchPoints);
        }
        return secondPlant;
    }

    // --- Pre-flight-gate contract: no timeline rows -> empty, no error ---

    @Test
    void supplyWithNoCoefficientRowsYieldsEmptyProductionWithoutError() {
        Supply supply = createTestedSupply();

        List<ProductionByTime> result = service.getHourlyProductionByRangeOfDatesAndSupply(
                OffsetDateTime.parse(START_DATE), OffsetDateTime.parse(END_DATE), SupplyId.of(supply.getId()));

        assertTrue(result.isEmpty());
    }

    // --- Case 1: transition (the killer) ---

    @Test
    void hourlyProductionAcrossATransitionUsesOldBetaBeforeAndNewBetaAfter() {
        Supply supply = createTestedSupply();
        persistCoefficient(supply.getId(), plant, BigDecimal.valueOf(0.3), Instant.parse("2020-01-01T00:00:00Z"), PEAK_HOUR);
        persistCoefficient(supply.getId(), plant, BigDecimal.valueOf(0.7), PEAK_HOUR, null);

        List<ProductionByTime> result = service.getHourlyProductionByRangeOfDatesAndSupply(
                OffsetDateTime.parse(START_DATE), OffsetDateTime.parse(END_DATE), SupplyId.of(supply.getId()));

        ProductionByTime hourBeforeTransition = result.stream()
                .filter(p -> p.getTime().toInstant().equals(PEAK_HOUR.minusSeconds(3600)))
                .findFirst().orElseThrow();
        ProductionByTime peakHour = result.stream()
                .filter(p -> p.getTime().toInstant().equals(PEAK_HOUR))
                .findFirst().orElseThrow();

        // Raw peak value is 31.1, and the hour immediately before it is 25.29 (see
        // EnergyProductionInfluxLoader); a single static beta covering the whole range could never
        // produce both 0.3-scaled and 0.7-scaled values in the same result.
        assertEquals(25.29d * 0.3d, hourBeforeTransition.getPower(), 0.01d);
        assertEquals(31.1d * 0.7d, peakHour.getPower(), 0.01d);
    }

    // --- Case 2: two plants ---

    @Test
    void hourlyProductionAcrossTwoPlantsSumsBothContributions() {
        Supply supply = createTestedSupply();
        Plant secondPlant = createSecondPlant(100.0d);
        persistCoefficient(supply.getId(), plant, BigDecimal.valueOf(0.4), Instant.parse("2020-01-01T00:00:00Z"), null);
        persistCoefficient(supply.getId(), secondPlant, BigDecimal.valueOf(0.6), Instant.parse("2020-01-01T00:00:00Z"), null);

        List<ProductionByTime> result = service.getHourlyProductionByRangeOfDatesAndSupply(
                OffsetDateTime.parse(START_DATE), OffsetDateTime.parse(END_DATE), SupplyId.of(supply.getId()));

        ProductionByTime peakHour = result.stream()
                .filter(p -> p.getTime().toInstant().equals(PEAK_HOUR))
                .findFirst().orElseThrow();

        assertEquals(31.1d * 0.4d + 100.0d * 0.6d, peakHour.getPower(), 0.01d);
    }

    // --- Case 5: monthly across a transition (local-calendar-day-fold, real DB) ---

    @Test
    void monthlyProductionEqualsSumOfCorrectlyScaledHourlyAcrossATransition() {
        Supply supply = createTestedSupply();
        persistCoefficient(supply.getId(), plant, BigDecimal.valueOf(0.3), Instant.parse("2020-01-01T00:00:00Z"), PEAK_HOUR);
        persistCoefficient(supply.getId(), plant, BigDecimal.valueOf(0.7), PEAK_HOUR, null);

        List<ProductionByTime> hourly = service.getHourlyProductionByRangeOfDatesAndSupply(
                OffsetDateTime.parse(START_DATE), OffsetDateTime.parse(END_DATE), SupplyId.of(supply.getId()));
        double expectedTotal = hourly.stream().mapToDouble(ProductionByTime::getPower).sum();

        List<ProductionByTime> monthly = service.getMonthlyProductionByRangeOfDatesAndSupply(
                OffsetDateTime.parse(START_DATE), OffsetDateTime.parse(END_DATE), SupplyId.of(supply.getId()));

        assertEquals(1, monthly.size());
        assertEquals(expectedTotal, monthly.get(0).getPower(), 0.01d);
    }

    // --- Case 6: community/plant totals unchanged (regression guard) ---

    @Test
    void communityTotalsRemainUnchangedRawAndUnscaled() {
        InstantProduction instant = service.getInstantProductionByCommunity(DEFAULT_COMMUNITY_ID);
        assertEquals(0.0d, instant.getPower(), 0.01d); // LAST() is the seeded night-time hour, value 0

        List<ProductionByTime> hourly = service.getHourlyProductionByRangeOfDatesAndCommunity(
                OffsetDateTime.parse(START_DATE), OffsetDateTime.parse(END_DATE), DEFAULT_COMMUNITY_ID);
        assertEquals(236.15d, hourly.stream().mapToDouble(ProductionByTime::getPower).sum(), 0.01d);

        List<ProductionByTime> daily = service.getDailyProductionByRangeOfDatesAndCommunity(
                OffsetDateTime.parse(START_DATE), OffsetDateTime.parse(END_DATE), DEFAULT_COMMUNITY_ID);
        assertEquals(236.15d, daily.stream().mapToDouble(ProductionByTime::getPower).sum(), 0.01d);

        List<ProductionByTime> monthly = service.getMonthlyProductionByRangeOfDatesAndCommunity(
                OffsetDateTime.parse(START_DATE), OffsetDateTime.parse(END_DATE), DEFAULT_COMMUNITY_ID);
        assertEquals(1, monthly.size());
        assertEquals(236.15d, monthly.get(0).getPower(), 0.01d, "Community monthly must still read the huawei_production_kwh_month pre-aggregate");

        List<ProductionByTime> yearly = service.getYearlyProductionByRangeOfDatesAndCommunity(
                OffsetDateTime.parse("2023-01-01T00:00:00.000+00:00"), OffsetDateTime.parse("2023-12-31T23:59:59.000+00:00"), DEFAULT_COMMUNITY_ID);
        assertEquals(1, yearly.size());
        assertEquals(1000.0d, yearly.get(0).getPower(), 0.01d, "Community yearly must still read the huawei_production_kwh_year pre-aggregate");
    }

    // --- Authorization-surface: intentional, documented asymmetry ---

    @Test
    void hourlyProductionFollowsTheCoefficientRowsPlantRegardlessOfCommunity() {
        // Pins the deliberate design choice documented in GetProductionServiceImpl: the plant set for
        // per-supply production comes entirely from supply_partition_coefficient. A supply with a
        // coefficient row pointing at a plant it has no community relationship with still gets that
        // plant's production -- this is intentional (the coefficient table IS the participation
        // relation), not a regression. Do not "fix" this by reintroducing a community filter.
        Supply supply = createTestedSupply(); // owned by an unrelated user/community, no plant of its own
        persistCoefficient(supply.getId(), plant, BigDecimal.valueOf(1.0), Instant.parse("2020-01-01T00:00:00Z"), null);

        List<ProductionByTime> result = service.getHourlyProductionByRangeOfDatesAndSupply(
                OffsetDateTime.parse(START_DATE), OffsetDateTime.parse(END_DATE), SupplyId.of(supply.getId()));

        assertEquals(236.15d, result.stream().mapToDouble(ProductionByTime::getPower).sum(), 0.01d);
    }
}
