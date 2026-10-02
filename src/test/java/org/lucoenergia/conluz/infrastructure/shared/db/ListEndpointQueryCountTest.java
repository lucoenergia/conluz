package org.lucoenergia.conluz.infrastructure.shared.db;

import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A listing must cost the same number of statements whatever its page holds. Anything else is an
 * N+1: it passes every functional test, and it degrades in production in proportion to how much
 * data a community has accumulated.
 *
 * <p>Each test measures one request over a single row and the same request over five, and asserts
 * the two counts are equal. The counts themselves are deliberately not asserted against a fixed
 * number -- that would break on any unrelated query added to the request path, and the property
 * that matters is the slope, not the intercept.</p>
 *
 * <p>The persistence context is flushed and cleared before each measurement, because the test
 * thread shares one with the request: without that, rows already managed from the arrange phase
 * would be served from the first-level cache and hide the very loads being counted.</p>
 */
@Transactional
class ListEndpointQueryCountTest extends BaseControllerTest {

    private static final Instant ACTIVATED = Instant.parse("2023-06-01T00:00:00Z");

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private CreateMembershipService createMembershipService;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private PlantRepository plantRepository;
    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;
    @Autowired
    private SaveSupplyPartitionCoefficientRepository saveCoefficientRepository;

    @Test
    void listingACommunitysSuppliesCostsTheSameForOneAndForFive() throws Exception {
        String adminToken = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        String url = "/api/v1/communities/" + DEFAULT_COMMUNITY_ID + "/supplies";

        persistSupplies(1);
        long forOne = statementsFor(url, adminToken);

        persistSupplies(4);
        long forFive = statementsFor(url, adminToken);

        assertEquals(forOne, forFive, "listing supplies must not issue a query per supply");
    }

    @Test
    void listingAUsersSuppliesCostsTheSameForOneAndForFive() throws Exception {
        String adminToken = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        // The owner must share a community with the caller, or canListSuppliesOfUser answers 404
        // before any supply is read.
        User owner = persistMember();
        String url = "/api/v1/users/" + owner.getId() + "/supplies";

        persistSuppliesOwnedBy(owner, 1);
        long forOne = statementsFor(url, adminToken);

        persistSuppliesOwnedBy(owner, 4);
        long forFive = statementsFor(url, adminToken);

        assertEquals(forOne, forFive, "listing a user's supplies must not issue a query per supply");
    }

    @Test
    void listingAUsersSuppliesCostsTheSameWhateverTheNumberOfCommunitiesInScope() throws Exception {
        // The scope is applied in one IN query, not one lookup per administered community: one supply
        // in one community must cost what five supplies spread over three communities cost.
        List<Community> communities = List.of(
                createCommunityRepository.create(CommunityMother.random().build()),
                createCommunityRepository.create(CommunityMother.random().build()),
                createCommunityRepository.create(CommunityMother.random().build()));
        User admin = UserMother.randomUser();
        admin.enable();
        createUserRepository.create(admin);
        User owner = persistUser();
        for (Community community : communities) {
            createMembershipService.create(community.getId(), admin.getId(), CommunityRole.COMMUNITY_ADMIN);
            createMembershipService.create(community.getId(), owner.getId(), CommunityRole.COMMUNITY_MEMBER);
        }
        String adminToken = loginUser(admin);
        String url = "/api/v1/users/" + owner.getId() + "/supplies";

        persistSupplyOwnedBy(owner, communities.get(0));
        long forOne = statementsFor(url, adminToken);

        persistSupplyOwnedBy(owner, communities.get(0));
        persistSupplyOwnedBy(owner, communities.get(1));
        persistSupplyOwnedBy(owner, communities.get(1));
        persistSupplyOwnedBy(owner, communities.get(2));
        long forFiveAcrossThree = statementsFor(url, adminToken);

        assertEquals(forOne, forFiveAcrossThree,
                "scoping a user's supplies must not issue a query per community or per supply");
    }

    @Test
    void listingACommunitysPlantsCostsTheSameForOneAndForFive() throws Exception {
        String memberToken = loginAsCommunityMember(DEFAULT_COMMUNITY_ID);
        String url = "/api/v1/communities/" + DEFAULT_COMMUNITY_ID + "/plants";

        persistPlants(1);
        long forOne = statementsFor(url, memberToken);

        persistPlants(4);
        long forFive = statementsFor(url, memberToken);

        assertEquals(forOne, forFive, "listing plants must not issue a query per plant");
    }

    @Test
    void listingCommunitiesCostsTheSameForOneAndForFive() throws Exception {
        String adminToken = loginAsDefaultPlatformAdmin();
        String url = "/api/v1/communities";

        persistCommunities(1);
        long forOne = statementsFor(url, adminToken);

        persistCommunities(4);
        long forFive = statementsFor(url, adminToken);

        assertEquals(forOne, forFive, "listing communities must not issue a query per community");
    }

    /**
     * The roster is where the user-capability batch has to hold: every member embedded in it carries
     * no memberships of its own, so a naive implementation would look them up one at a time.
     */
    @Test
    void listingACommunitysMembershipsCostsTheSameForOneAndForFive() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        String adminToken = loginAsCommunityAdmin(community.getId());
        String url = "/api/v1/communities/" + community.getId() + "/memberships";

        persistMembersOf(community, 1);
        long forOne = statementsFor(url, adminToken);

        persistMembersOf(community, 4);
        long forFive = statementsFor(url, adminToken);

        assertEquals(forOne, forFive, "listing memberships must not issue a query per member");
    }

    @Test
    void listingUsersCostsTheSameForOneAndForFive() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        String adminToken = loginAsCommunityAdmin(community.getId());
        String url = "/api/v1/users";

        persistMembersOf(community, 1);
        long forOne = statementsFor(url, adminToken);

        persistMembersOf(community, 4);
        long forFive = statementsFor(url, adminToken);

        assertEquals(forOne, forFive, "listing users must not issue a query per user");
    }

    @Test
    void listingUsersCostsTheSameWhateverTheNumberOfCommunitiesInScope() throws Exception {
        // The scope is applied in one statement, not one lookup per administered community: one user
        // in one community must cost what five users spread over three communities cost.
        List<Community> communities = List.of(
                createCommunityRepository.create(CommunityMother.random().build()),
                createCommunityRepository.create(CommunityMother.random().build()),
                createCommunityRepository.create(CommunityMother.random().build()));
        User admin = UserMother.randomUser();
        admin.enable();
        createUserRepository.create(admin);
        for (Community community : communities) {
            createMembershipService.create(community.getId(), admin.getId(), CommunityRole.COMMUNITY_ADMIN);
        }
        String adminToken = loginUser(admin);
        String url = "/api/v1/users";

        persistMembersOf(communities.get(0), 1);
        long forOne = statementsFor(url, adminToken);

        persistMembersOf(communities.get(0), 1);
        persistMembersOf(communities.get(1), 2);
        persistMembersOf(communities.get(2), 1);
        long forFiveAcrossThree = statementsFor(url, adminToken);

        assertEquals(forOne, forFiveAcrossThree,
                "scoping the users listing must not issue a query per community or per user");
    }

    /**
     * The plant behind the agreements is resolved once for the page, not once per agreement.
     */
    @Test
    void listingAPlantsSharingAgreementsCostsTheSameForOneAndForFive() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        Supply supply = createSupplyRepository.create(SupplyMother.random().build(),
                UserId.of(persistUser().getId()), community.getId());
        Plant plant = createPlantRepository.create(PlantMother.random(supply).build(),
                SupplyId.of(supply.getId()));
        String adminToken = loginAsCommunityAdmin(community.getId());
        String url = "/api/v1/plants/" + plant.getId() + "/sharing-agreements";

        persistAgreements(plant, 1);
        long forOne = statementsFor(url, adminToken);

        persistAgreements(plant, 4);
        long forFive = statementsFor(url, adminToken);

        assertEquals(forOne, forFive, "listing sharing agreements must not issue a query per agreement");
    }

    /**
     * The coefficient capabilities load every distinct plant and agreement once for the whole list,
     * so five periods spread over three plants cost what one period in one plant does.
     */
    @Test
    void aSupplysCoefficientHistoryCostsTheSameForOnePeriodAndForFiveAcrossThreePlants() throws Exception {
        String adminToken = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply one = persistSupplyInDefaultCommunity();
        persistPeriods(one, 1, 1);
        Supply five = persistSupplyInDefaultCommunity();
        persistPeriods(five, 3, 5);

        long forOne = statementsFor("/api/v1/supplies/" + one.getId() + "/partition-coefficients", adminToken);
        long forFive = statementsFor("/api/v1/supplies/" + five.getId() + "/partition-coefficients", adminToken);

        assertEquals(forOne, forFive, "a coefficient history must not issue a query per period or per plant");
    }

    @Test
    void aSupplysActiveCoefficientsCostTheSameForOnePlantAndForThree() throws Exception {
        String adminToken = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply one = persistSupplyInDefaultCommunity();
        persistPeriods(one, 1, 1);
        Supply three = persistSupplyInDefaultCommunity();
        persistPeriods(three, 3, 3);

        long forOne = statementsFor("/api/v1/supplies/" + one.getId() + "/partition-coefficients/active", adminToken);
        long forThree = statementsFor("/api/v1/supplies/" + three.getId() + "/partition-coefficients/active",
                adminToken);

        assertEquals(forOne, forThree, "active coefficients must not issue a query per plant");
    }

    @Test
    void aPlantsActiveCoefficientsCostTheSameForOneSupplyAndForFive() throws Exception {
        String adminToken = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply plantSupply = persistSupplyInDefaultCommunity();
        Plant plant = createPlantRepository.create(PlantMother.random(plantSupply).build(),
                SupplyId.of(plantSupply.getId()));
        SharingAgreementEntity agreement = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        String url = "/api/v1/plants/" + plant.getId() + "/partition-coefficients/active";

        persistActiveCoefficient(persistSupplyInDefaultCommunity(), plant, agreement, ACTIVATED);
        long forOne = statementsFor(url, adminToken);

        for (int i = 0; i < 4; i++) {
            persistActiveCoefficient(persistSupplyInDefaultCommunity(), plant, agreement, ACTIVATED);
        }
        long forFive = statementsFor(url, adminToken);

        assertEquals(forOne, forFive, "a plant's active coefficients must not issue a query per supply");
    }

    private long statementsFor(String url, String authHeader) throws Exception {
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());

        return statistics.getPrepareStatementCount();
    }

    private void persistSupplies(int count) {
        for (int i = 0; i < count; i++) {
            persistSuppliesOwnedBy(persistUser(), 1);
        }
    }

    private void persistSuppliesOwnedBy(User owner, int count) {
        for (int i = 0; i < count; i++) {
            createSupplyRepository.create(SupplyMother.random().build(), UserId.of(owner.getId()));
        }
    }

    private void persistSupplyOwnedBy(User owner, Community community) {
        createSupplyRepository.create(SupplyMother.random().build(), UserId.of(owner.getId()), community.getId());
    }

    private void persistPlants(int count) {
        for (int i = 0; i < count; i++) {
            Supply supply = createSupplyRepository.create(SupplyMother.random().build(),
                    UserId.of(persistUser().getId()));
            createPlantRepository.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId()));
        }
    }

    private User persistUser() {
        User user = UserMother.randomUser();
        createUserRepository.create(user);
        return user;
    }

    private User persistMember() {
        User user = persistUser();
        createMembershipService.create(DEFAULT_COMMUNITY_ID, user.getId(), CommunityRole.COMMUNITY_MEMBER);
        return user;
    }

    private void persistCommunities(int count) {
        for (int i = 0; i < count; i++) {
            createCommunityRepository.create(CommunityMother.random().build());
        }
    }

    private void persistMembersOf(Community community, int count) {
        for (int i = 0; i < count; i++) {
            User user = persistUser();
            createMembershipService.create(community.getId(), user.getId(), CommunityRole.COMMUNITY_MEMBER);
        }
    }

    private Supply persistSupplyInDefaultCommunity() {
        return createSupplyRepository.create(SupplyMother.random().build(), UserId.of(persistUser().getId()),
                DEFAULT_COMMUNITY_ID);
    }

    /**
     * {@code periods} consecutive periods of the supply spread round-robin over {@code plants} plants,
     * each plant with an agreement of its own. The last period of each plant stays open.
     */
    private void persistPeriods(Supply supply, int plants, int periods) {
        List<Plant> plantList = new ArrayList<>();
        List<SharingAgreementEntity> agreements = new ArrayList<>();
        for (int i = 0; i < plants; i++) {
            Supply plantSupply = persistSupplyInDefaultCommunity();
            Plant plant = createPlantRepository.create(PlantMother.random(plantSupply).build(),
                    SupplyId.of(plantSupply.getId()));
            plantList.add(plant);
            agreements.add(persistAgreement(plant, SharingAgreementStatus.PUBLISHED));
        }
        int perPlant = (periods + plants - 1) / plants;
        int created = 0;
        for (int i = 0; i < plants && created < periods; i++) {
            for (int k = 0; k < perPlant && created < periods; k++, created++) {
                Instant from = ACTIVATED.plus(Duration.ofDays(30L * k));
                boolean last = k == perPlant - 1 || created == periods - 1;
                persistCoefficient(supply, plantList.get(i), agreements.get(i), from,
                        last ? null : from.plus(Duration.ofDays(30)));
            }
        }
    }

    private void persistActiveCoefficient(Supply supply, Plant plant, SharingAgreementEntity agreement, Instant from) {
        persistCoefficient(supply, plant, agreement, from, null);
    }

    private void persistCoefficient(Supply supply, Plant plant, SharingAgreementEntity agreement, Instant from,
                                    Instant to) {
        saveCoefficientRepository.save(new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supply.getId())
                .withPlantId(plant.getId())
                .withSharingAgreementId(agreement.getId())
                .withCoefficient(BigDecimal.ONE)
                .withValidFrom(from)
                .withValidTo(to)
                .withCreatedAt(Instant.now())
                .build());
    }

    private SharingAgreementEntity persistAgreement(Plant plant, SharingAgreementStatus status) {
        SharingAgreementEntity agreement = new SharingAgreementEntity();
        agreement.setId(UUID.randomUUID());
        agreement.setPlant(plantRepository.getReferenceById(plant.getId()));
        agreement.setName("Agreement " + UUID.randomUUID());
        agreement.setStatus(status);
        agreement.setCreatedAt(Instant.now());
        return sharingAgreementRepository.save(agreement);
    }

    private void persistAgreements(Plant plant, int count) {
        for (int i = 0; i < count; i++) {
            PlantEntity plantEntity = plantRepository.getReferenceById(plant.getId());
            SharingAgreementEntity agreement = new SharingAgreementEntity();
            agreement.setId(UUID.randomUUID());
            agreement.setPlant(plantEntity);
            agreement.setName("Agreement " + UUID.randomUUID());
            agreement.setStatus(SharingAgreementStatus.DRAFT);
            agreement.setCreatedAt(Instant.now());
            sharingAgreementRepository.save(agreement);
        }
    }
}
