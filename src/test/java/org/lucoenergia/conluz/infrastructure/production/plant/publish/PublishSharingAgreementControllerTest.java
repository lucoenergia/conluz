package org.lucoenergia.conluz.infrastructure.production.plant.publish;

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
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionCoefficientEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionCoefficientJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class PublishSharingAgreementControllerTest extends BaseControllerTest {

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
    private SupplyRepository supplyRepository;
    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;
    @Autowired
    private SupplyPartitionCoefficientJpaRepository supplyPartitionCoefficientJpaRepository;

    private Community communityA;
    private Community communityB;
    private Plant plantA;
    private Supply supplyA;
    private Plant otherPlantInCommunityA;
    private SharingAgreementEntity draftAgreement;

    @BeforeEach
    void setUp() {
        communityA = createCommunityRepository.create(CommunityMother.random().build());
        communityB = createCommunityRepository.create(CommunityMother.random().build());
        supplyA = createSupply(communityA);
        plantA = createPlant(supplyA);
        otherPlantInCommunityA = createPlant(createSupply(communityA));
        draftAgreement = createAgreement(plantA, SharingAgreementStatus.DRAFT);
    }

    @Test
    void returnsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(post(url(plantA.getId(), draftAgreement.getId()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsForbiddenForCommunityMember() throws Exception {
        String authHeader = loginAsCommunityMember(communityA.getId());

        mockMvc.perform(post(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsNotFoundForCrossCommunityAdmin() throws Exception {
        String authHeader = loginAsCommunityAdmin(communityB.getId());

        mockMvc.perform(post(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundWhenAgreementBelongsToAnotherPlantOfTheSameCommunity() throws Exception {
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(otherPlantInCommunityA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsConflictWhenAgreementIsNotDraft() throws Exception {
        SharingAgreementEntity published = createAgreement(plantA, SharingAgreementStatus.PUBLISHED);
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(plantA.getId(), published.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isConflict());
    }

    @Test
    void returnsConflictWhenAgreementHasNoCoefficients() throws Exception {
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isConflict());
    }

    @Test
    void publishesDraftAgreementWithCoefficients() throws Exception {
        seedCoefficient(supplyA, plantA, draftAgreement);
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(draftAgreement.getId().toString()))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    private Supply createSupply(Community community) {
        User owner = UserMother.randomUser();
        createUserRepository.create(owner);
        Supply supply = SupplyMother.random(owner).build();
        return createSupplyRepository.create(supply, UserId.of(owner.getId()), community.getId());
    }

    private Plant createPlant(Supply supply) {
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

    private void seedCoefficient(Supply supply, Plant plant, SharingAgreementEntity agreement) {
        SupplyEntity supplyEntity = supplyRepository.getReferenceById(supply.getId());
        PlantEntity plantEntity = plantRepository.getReferenceById(plant.getId());
        SharingAgreementEntity agreementReference = sharingAgreementRepository.getReferenceById(agreement.getId());

        SupplyPartitionCoefficientEntity coefficient = new SupplyPartitionCoefficientEntity();
        coefficient.setId(UUID.randomUUID());
        coefficient.setSupply(supplyEntity);
        coefficient.setPlant(plantEntity);
        coefficient.setSharingAgreement(agreementReference);
        coefficient.setCoefficient(BigDecimal.ONE);
        coefficient.setValidFrom(Instant.now());
        coefficient.setCreatedAt(Instant.now());
        supplyPartitionCoefficientJpaRepository.save(coefficient);
    }

    private String url(UUID plantId, UUID agreementId) {
        return "/api/v1/plants/" + plantId + "/sharing-agreements/" + agreementId + "/publish";
    }
}
