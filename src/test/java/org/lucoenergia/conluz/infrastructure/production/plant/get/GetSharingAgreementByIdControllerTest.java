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
import org.lucoenergia.conluz.domain.production.plant.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.SharingAgreementRepository;
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
 * Security matrix and happy-path tests for the single sharing-agreement read endpoint. Uses two
 * communities, plus a second plant within the same community, so both the cross-community IDOR
 * and the wrong-plant-same-community cases can be exercised.
 */
@Transactional
class GetSharingAgreementByIdControllerTest extends BaseControllerTest {

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
    private Plant otherPlantInCommunityA;
    private SharingAgreementEntity agreementOfPlantA;

    @BeforeEach
    void setUp() {
        communityA = createCommunityRepository.create(CommunityMother.random().build());
        communityB = createCommunityRepository.create(CommunityMother.random().build());

        plantA = createPlant(communityA);
        otherPlantInCommunityA = createPlant(communityA);

        agreementOfPlantA = createAgreement(plantA);
    }

    @Test
    void returnsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get(url(plantA.getId(), agreementOfPlantA.getId()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsNotFoundForCrossCommunityMember() throws Exception {
        // A member of a different community cannot see plantA -> 404, not 403.
        String authHeader = loginAsCommunityMember(communityB.getId());

        mockMvc.perform(get(url(plantA.getId(), agreementOfPlantA.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundWhenAgreementBelongsToAnotherPlantOfTheSameCommunity() throws Exception {
        // The caller can see the community and the path plant, but the agreement belongs to a
        // different plant -> 404, it must not be served.
        String authHeader = loginAsCommunityMember(communityA.getId());

        mockMvc.perform(get(url(otherPlantInCommunityA.getId(), agreementOfPlantA.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsAgreementForCommunityMember() throws Exception {
        String authHeader = loginAsCommunityMember(communityA.getId());

        mockMvc.perform(get(url(plantA.getId(), agreementOfPlantA.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(agreementOfPlantA.getId().toString()))
                .andExpect(jsonPath("$.plantId").value(plantA.getId().toString()))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    private Plant createPlant(Community community) {
        User owner = UserMother.randomUser();
        createUserRepository.create(owner);
        Supply supply = SupplyMother.random(owner).build();
        supply = createSupplyRepository.create(supply, UserId.of(owner.getId()), community.getId());
        Plant plant = PlantMother.random(supply).build();
        return createPlantRepository.create(plant, SupplyId.of(supply.getId()));
    }

    private SharingAgreementEntity createAgreement(Plant plant) {
        PlantEntity plantEntity = plantRepository.getReferenceById(plant.getId());
        SharingAgreementEntity agreement = new SharingAgreementEntity();
        agreement.setId(UUID.randomUUID());
        agreement.setPlant(plantEntity);
        agreement.setName("Test agreement " + UUID.randomUUID());
        agreement.setStatus(SharingAgreementStatus.PUBLISHED);
        agreement.setCreatedAt(Instant.now());
        agreement.setCreatedBy(null);
        return sharingAgreementRepository.save(agreement);
    }

    private String url(UUID plantId, UUID agreementId) {
        return "/api/v1/plants/" + plantId + "/sharing-agreements/" + agreementId;
    }
}
