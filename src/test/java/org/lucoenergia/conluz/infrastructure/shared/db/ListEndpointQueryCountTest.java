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
import java.time.Instant;
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
