package org.lucoenergia.conluz.infrastructure.production.get;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
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
import org.lucoenergia.conluz.domain.production.ProductionByTime;
import org.lucoenergia.conluz.domain.production.get.GetProductionService;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.production.ProductionPoint;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces the cross-community leakage in per-supply production queries: without a station
 * restriction derived from the supply's own community, a supply reports production from every
 * plant of every community, not just its own.
 */
@SpringBootTest
class GetProductionServiceImplIntegrationTest extends BaseIntegrationTest {

    private static final String STATION_CODE_A = "NE=99900001";
    private static final String STATION_CODE_B = "NE=99900002";
    private static final OffsetDateTime START_DATE = OffsetDateTime.parse("2023-09-01T00:00:00.000+02:00");
    private static final OffsetDateTime END_DATE = OffsetDateTime.parse("2023-09-01T01:00:00.000+02:00");
    private static final long SEEDED_HOUR_NANOS = 1693519200000000000L; // 2023-09-01T00:00:00Z

    @Autowired
    private GetProductionService getProductionService;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private InfluxDbConnectionManager influxDbConnectionManager;

    private void seedStationProduction(String stationCode, double inverterPower) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();
            batchPoints.point(Point.measurement(HuaweiConfig.HUAWEI_HOURLY_PRODUCTION_MEASUREMENT)
                    .time(SEEDED_HOUR_NANOS, TimeUnit.NANOSECONDS)
                    .tag("station_code", stationCode)
                    .addField(ProductionPoint.INVERTER_POWER, inverterPower)
                    .build());
            connection.write(batchPoints);
        }
    }

    private Supply createSupplyInNewCommunityWithPlant(String stationCode) {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(
                SupplyMother.random().withPartitionCoefficient(1.0f).build(),
                UserId.of(user.getId()), community.getId());
        createPlantRepository.create(
                PlantMother.random(supply).withProviderCode(stationCode).build(), SupplyId.of(supply.getId()));
        return supply;
    }

    @Test
    void getHourlyProductionByRangeOfDatesAndSupply_reportsOnlyOwnCommunityProduction() {
        seedStationProduction(STATION_CODE_A, 10.0d);
        seedStationProduction(STATION_CODE_B, 1000.0d);

        Supply supplyA = createSupplyInNewCommunityWithPlant(STATION_CODE_A);
        createSupplyInNewCommunityWithPlant(STATION_CODE_B);

        List<ProductionByTime> result = getProductionService.getHourlyProductionByRangeOfDatesAndSupply(
                START_DATE, END_DATE, SupplyId.of(supplyA.getId()));

        assertEquals(1, result.size());
        assertEquals(10.0d, result.get(0).getPower(), 0.01d,
                "Supply A must report only community A's production, not A+B combined");
    }

    @Test
    void getHourlyProductionByRangeOfDatesAndSupply_communityWithNoPlantYieldsEmptyResult() {
        Community communityWithNoPlant = createCommunityRepository.create(CommunityMother.random().build());
        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(
                SupplyMother.random().withPartitionCoefficient(1.0f).build(),
                UserId.of(user.getId()), communityWithNoPlant.getId());

        List<ProductionByTime> result = getProductionService.getHourlyProductionByRangeOfDatesAndSupply(
                START_DATE, END_DATE, SupplyId.of(supply.getId()));

        assertTrue(result.isEmpty(), "A community with no plants must yield an empty result, never an unrestricted query");
    }
}
