package org.lucoenergia.conluz.infrastructure.production.plant.get;

import org.junit.jupiter.api.BeforeEach;
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
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security matrix and happy-path tests for the sharing-agreement list endpoint. Uses two
 * communities so the cross-community leak (IDOR) case can be exercised.
 */
@Transactional
class GetSharingAgreementsControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private PlantRepository plantRepository;
    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;

    private Community communityA;
    private Community communityB;
    private Plant plantA;

    @BeforeEach
    void setUp() {
        communityA = createCommunityRepository.create(CommunityMother.random().build());
        communityB = createCommunityRepository.create(CommunityMother.random().build());

        User owner = UserMother.randomUser();
        createUserRepository.create(owner);
        Supply supplyA = SupplyMother.random(owner).build();
        supplyA = createSupplyRepository.create(supplyA, UserId.of(owner.getId()), communityA.getId());
        plantA = PlantMother.random(supplyA).build();
        plantA = createPlantRepository.create(plantA, SupplyId.of(supplyA.getId()));
    }

    @Test
    void returnsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get(url(plantA.getId()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsNotFoundWhenCallerIsNotMemberOfPlantCommunity() throws Exception {
        // A member of a different community cannot see plantA -> 404, not 403.
        String authHeader = loginAsCommunityMember(communityB.getId());

        mockMvc.perform(get(url(plantA.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsAgreementsForCommunityMemberOrderedNewestFirst() throws Exception {
        Instant now = Instant.now();
        createAgreement(plantA, SharingAgreementStatus.DRAFT, now.minusSeconds(60));
        SharingAgreementEntity newest = createAgreement(plantA, SharingAgreementStatus.PUBLISHED, now);

        String authHeader = loginAsCommunityMember(communityA.getId());

        mockMvc.perform(get(url(plantA.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(newest.getId().toString()));
    }

    @Test
    void filtersByStatus() throws Exception {
        createAgreement(plantA, SharingAgreementStatus.DRAFT, Instant.now().minusSeconds(60));
        SharingAgreementEntity published = createAgreement(plantA, SharingAgreementStatus.PUBLISHED, Instant.now());

        String authHeader = loginAsCommunityMember(communityA.getId());

        mockMvc.perform(get(url(plantA.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("status", "PUBLISHED")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(published.getId().toString()))
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"));
    }

    private SharingAgreementEntity createAgreement(Plant plant, SharingAgreementStatus status, Instant createdAt) {
        PlantEntity plantEntity = plantRepository.getReferenceById(plant.getId());
        SharingAgreementEntity agreement = new SharingAgreementEntity();
        agreement.setId(UUID.randomUUID());
        agreement.setPlant(plantEntity);
        agreement.setName("Test agreement " + UUID.randomUUID());
        agreement.setStatus(status);
        agreement.setCreatedAt(createdAt);
        agreement.setCreatedBy(null);
        return sharingAgreementRepository.save(agreement);
    }

    private String url(UUID plantId) {
        return "/api/v1/plants/" + plantId + "/sharing-agreements";
    }
}
