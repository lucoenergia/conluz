package org.lucoenergia.conluz.infrastructure.production.get;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.production.ProductionPoint;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Which {@code supplyId} the five Huawei community production reads accept (CPR-001): only the
 * backing supply of a plant of the community in the path, whoever calls.
 *
 * <p>The community holds two plants and two consumer supplies owned by different members. Plant A
 * reads a station seeded here, under a station code of its own, so the expected values do not
 * depend on what other tests write; plant B's station has no data and its supply no coefficient.</p>
 */
@Transactional
class CommunityProductionSupplyIdControllerTest extends BaseControllerTest {

    private static final String START_DATE = "2023-09-01T00:00:00.000+02:00";
    private static final String END_DATE = "2023-09-01T23:00:00.000+02:00";
    /**
     * Plant A's share of its own station, applied to every seeded point.
     */
    private static final BigDecimal PLANT_A_COEFFICIENT = new BigDecimal("0.5");

    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private CreateSharingAgreementRepository createSharingAgreementRepository;
    @Autowired
    private SaveSupplyPartitionCoefficientRepository saveSupplyPartitionCoefficientRepository;
    @Autowired
    private InfluxDbConnectionManager influxDbConnectionManager;

    private Community community;
    private User caller;
    private Supply callerConsumerSupply;
    private Supply otherMemberConsumerSupply;
    private Supply plantASupply;
    private Supply plantBSupply;
    private Supply otherCommunityPlantSupply;

    /**
     * One of the five operations, and the body it answers for plant A's backing supply, derived
     * from the points seeded in {@link #seedPlantAStation(String)} times {@link #PLANT_A_COEFFICIENT}.
     */
    private record Operation(String name, String path, boolean series, List<Double> plantAPowers) {

        MockHttpServletRequestBuilder request(UUID communityId) {
            MockHttpServletRequestBuilder request = get("/api/v1/communities/" + communityId + "/production" + path);
            return series ? request.queryParam("startDate", START_DATE).queryParam("endDate", END_DATE) : request;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static Stream<Operation> operations() {
        return Stream.of(
                // The latest point of each plant's station (10.0), times the coefficient now in force.
                new Operation("getInstantProduction", "", false, List.of(5.0)),
                // Each seeded hour (4.0, 6.0, 10.0), times the coefficient.
                new Operation("getHourlyProduction", "/hourly", true, List.of(2.0, 3.0, 5.0)),
                // The three hours fall in one UTC day, one local month and one local year: 20.0 x 0.5.
                new Operation("getDailyProduction", "/daily", true, List.of(10.0)),
                new Operation("getMonthlyProduction", "/monthly", true, List.of(10.0)),
                new Operation("getYearlyProduction", "/yearly", true, List.of(10.0)));
    }

    @BeforeEach
    void beforeEach() {
        community = createCommunityRepository.create(CommunityMother.random().build());
        Community otherCommunity = createCommunityRepository.create(CommunityMother.random().build());

        caller = memberOf(community);
        User otherMember = memberOf(community);
        User plantOwner = memberOf(community);

        callerConsumerSupply = supplyOf(caller, community);
        otherMemberConsumerSupply = supplyOf(otherMember, community);

        String plantAStationCode = "CPR001-A-" + UUID.randomUUID();
        plantASupply = supplyOf(plantOwner, community);
        Plant plantA = createPlantRepository.create(
                PlantMother.random(plantASupply).withProviderCode(plantAStationCode).build(),
                SupplyId.of(plantASupply.getId()));
        plantBSupply = supplyOf(plantOwner, community);
        createPlantRepository.create(
                PlantMother.random(plantBSupply).withProviderCode("CPR001-B-" + UUID.randomUUID()).build(),
                SupplyId.of(plantBSupply.getId()));

        User otherCommunityOwner = memberOf(otherCommunity);
        otherCommunityPlantSupply = supplyOf(otherCommunityOwner, otherCommunity);
        createPlantRepository.create(PlantMother.random(otherCommunityPlantSupply).build(),
                SupplyId.of(otherCommunityPlantSupply.getId()));

        holdCoefficient(plantASupply, plantA, plantOwner);
        seedPlantAStation(plantAStationCode);
    }

    // --- rejected: 404, with the same error as the Datadis production reads ---

    @ParameterizedTest(name = "CPR-001 {0} answers 404 for another member's consumer supply")
    @MethodSource("operations")
    void cpr001RejectsAnotherMembersConsumerSupply(Operation operation) throws Exception {
        assertNotFound(read(operation, otherMemberConsumerSupply.getId()));
    }

    @ParameterizedTest(name = "CPR-001 {0} answers 404 for the caller's own consumer supply")
    @MethodSource("operations")
    void cpr001RejectsTheCallersOwnConsumerSupply(Operation operation) throws Exception {
        assertNotFound(read(operation, callerConsumerSupply.getId()));
    }

    @ParameterizedTest(name = "CPR-001 {0} answers 404 for a plant supply of another community")
    @MethodSource("operations")
    void cpr001RejectsASupplyOfAnotherCommunity(Operation operation) throws Exception {
        assertNotFound(read(operation, otherCommunityPlantSupply.getId()));
    }

    @ParameterizedTest(name = "CPR-001 {0} answers 404 for a supply that does not exist")
    @MethodSource("operations")
    void cpr001RejectsANonexistentSupply(Operation operation) throws Exception {
        assertNotFound(read(operation, UUID.randomUUID()));
    }

    // --- accepted: every plant's backing supply, with the body the operation answered before ---

    @ParameterizedTest(name = "CPR-001 {0} accepts plant A's backing supply and weights its production by the coefficient")
    @MethodSource("operations")
    void cpr001AcceptsTheBackingSupplyOfAPlant(Operation operation) throws Exception {
        ResultActions result = read(operation, plantASupply.getId()).andExpect(status().isOk());

        if (operation.series()) {
            result.andExpect(jsonPath("$", hasSize(operation.plantAPowers().size())))
                    .andExpect(jsonPath("$[*].power", contains(operation.plantAPowers().toArray())));
        } else {
            result.andExpect(jsonPath("$.power").value(operation.plantAPowers().get(0)));
        }
    }

    @ParameterizedTest(name = "CPR-001 {0} accepts plant B's backing supply, which holds no coefficient")
    @MethodSource("operations")
    void cpr001AcceptsTheBackingSupplyOfTheOtherPlant(Operation operation) throws Exception {
        ResultActions result = read(operation, plantBSupply.getId()).andExpect(status().isOk());

        // A supply with no coefficient row is allocated nothing: an empty series, or an instant power of 0.0.
        if (operation.series()) {
            result.andExpect(jsonPath("$", hasSize(0)));
        } else {
            result.andExpect(jsonPath("$.power").value(0.0));
        }
    }

    // --- the guard is unchanged: a caller who is not a member is refused before supplyId is looked at ---

    @ParameterizedTest(name = "{0} answers 404 to an authenticated non-member, even for a plant's backing supply")
    @MethodSource("operations")
    void nonMemberIsRefusedWhateverTheSupply(Operation operation) throws Exception {
        mockMvc.perform(operation.request(community.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginAsPartner())
                        .queryParam("supplyId", plantASupply.getId().toString()))
                .andExpect(status().isNotFound());
    }

    // --- helpers ---

    private ResultActions read(Operation operation, UUID supplyId) throws Exception {
        return mockMvc.perform(operation.request(community.getId())
                .header(HttpHeaders.AUTHORIZATION, loginUser(caller))
                .queryParam("supplyId", supplyId.toString()));
    }

    private static void assertNotFound(ResultActions result) throws Exception {
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].code").value(nullValue()));
    }

    private User memberOf(Community target) {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        createMembershipRepository.create(target.getId(), user.getId(), CommunityRole.COMMUNITY_MEMBER);
        return user;
    }

    private Supply supplyOf(User owner, Community target) {
        return createSupplyRepository.create(SupplyMother.random(owner).withEnabled(true).build(),
                UserId.of(owner.getId()), target.getId());
    }

    private void holdCoefficient(Supply supply, Plant plant, User createdBy) {
        SharingAgreement agreement = createSharingAgreementRepository.create(new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withPlantId(plant.getId())
                .withName("Agreement " + UUID.randomUUID())
                .withStatus(SharingAgreementStatus.PUBLISHED)
                .withNominalPowerKW(BigDecimal.TEN)
                .withCreatedAt(Instant.now())
                .withCreatedBy(createdBy.getId())
                .build());
        saveSupplyPartitionCoefficientRepository.save(new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supply.getId())
                .withPlantId(plant.getId())
                .withSharingAgreementId(agreement.getId())
                .withCoefficient(PLANT_A_COEFFICIENT)
                .withValidFrom(Instant.parse("2020-01-01T00:00:00Z"))
                .withValidTo(null)
                .withCreatedAt(Instant.now())
                .build());
    }

    /**
     * Three hourly points on 2023-09-01, inside the requested period: 4.0 at 08:00Z, 6.0 at 09:00Z
     * and 10.0 at 10:00Z, the latest point the station has.
     */
    private void seedPlantAStation(String stationCode) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();
            batchPoints.point(hourlyPoint(stationCode, "2023-09-01T08:00:00Z", 4.0));
            batchPoints.point(hourlyPoint(stationCode, "2023-09-01T09:00:00Z", 6.0));
            batchPoints.point(hourlyPoint(stationCode, "2023-09-01T10:00:00Z", 10.0));
            connection.write(batchPoints);
        }
    }

    private static Point hourlyPoint(String stationCode, String time, double power) {
        return Point.measurement(HuaweiConfig.HUAWEI_HOURLY_PRODUCTION_MEASUREMENT)
                .time(Instant.parse(time).toEpochMilli(), TimeUnit.MILLISECONDS)
                .tag("station_code", stationCode)
                .addField(ProductionPoint.INVERTER_POWER, power)
                .build();
    }
}
