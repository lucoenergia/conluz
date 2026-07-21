package org.lucoenergia.conluz.infrastructure.production.plant.update;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class UpdateSharingAgreementControllerTest extends BaseControllerTest {

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
    private SharingAgreementEntity draftAgreement;

    @BeforeEach
    void setUp() {
        communityA = createCommunityRepository.create(CommunityMother.random().build());
        communityB = createCommunityRepository.create(CommunityMother.random().build());
        plantA = createPlant(communityA);
        otherPlantInCommunityA = createPlant(communityA);
        draftAgreement = createAgreement(plantA, SharingAgreementStatus.DRAFT);
    }

    @Test
    void returnsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(patch(url(plantA.getId(), draftAgreement.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("New name", "5.5")))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsForbiddenForCommunityMember() throws Exception {
        String authHeader = loginAsCommunityMember(communityA.getId());

        mockMvc.perform(patch(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("New name", "5.5")))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsNotFoundForCrossCommunityAdmin() throws Exception {
        String authHeader = loginAsCommunityAdmin(communityB.getId());

        mockMvc.perform(patch(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("New name", "5.5")))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundWhenAgreementBelongsToAnotherPlantOfTheSameCommunity() throws Exception {
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(patch(url(otherPlantInCommunityA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("New name", "5.5")))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsConflictWhenAgreementIsNotDraft() throws Exception {
        SharingAgreementEntity published = createAgreement(plantA, SharingAgreementStatus.PUBLISHED);
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(patch(url(plantA.getId(), published.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("New name", "5.5")))
                .andDo(print())
                .andExpect(status().isConflict());
    }

    @Test
    void updatesOnlyDescriptiveFields() throws Exception {
        String authHeader = loginAsCommunityAdmin(communityA.getId());
        Instant originalCreatedAt = draftAgreement.getCreatedAt();

        mockMvc.perform(patch(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Updated name", "9.75")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(draftAgreement.getId().toString()))
                .andExpect(jsonPath("$.plantId").value(plantA.getId().toString()))
                .andExpect(jsonPath("$.name").value("Updated name"))
                .andExpect(jsonPath("$.installedPowerKw").value(9.75))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        SharingAgreementEntity persisted = sharingAgreementRepository.findById(draftAgreement.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(plantA.getId(), persisted.getPlant().getId());
        org.junit.jupiter.api.Assertions.assertEquals(originalCreatedAt, persisted.getCreatedAt());
        org.junit.jupiter.api.Assertions.assertNull(persisted.getCreatedBy());
        org.junit.jupiter.api.Assertions.assertEquals(SharingAgreementStatus.DRAFT, persisted.getStatus());
    }

    private Plant createPlant(Community community) {
        User owner = UserMother.randomUser();
        createUserRepository.create(owner);
        Supply supply = SupplyMother.random(owner).build();
        supply = createSupplyRepository.create(supply, UserId.of(owner.getId()), community.getId());
        Plant plant = PlantMother.random(supply).build();
        return createPlantRepository.create(plant, SupplyId.of(supply.getId()));
    }

    private SharingAgreementEntity createAgreement(Plant plant, SharingAgreementStatus status) {
        PlantEntity plantEntity = plantRepository.getReferenceById(plant.getId());
        SharingAgreementEntity agreement = new SharingAgreementEntity();
        agreement.setId(UUID.randomUUID());
        agreement.setPlant(plantEntity);
        agreement.setName("Test agreement " + UUID.randomUUID());
        agreement.setStatus(status);
        agreement.setCreatedAt(Instant.now());
        agreement.setCreatedBy(null);
        return sharingAgreementRepository.save(agreement);
    }

    private String body(String name, String installedPowerKw) {
        return "{\"name\":\"" + name + "\",\"installedPowerKw\":" + installedPowerKw + "}";
    }

    private String url(UUID plantId, UUID agreementId) {
        return "/api/v1/plants/" + plantId + "/sharing-agreements/" + agreementId;
    }
}
