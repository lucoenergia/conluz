package org.lucoenergia.conluz.infrastructure.production.sharingagreement.revert;

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
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionCoefficientEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionCoefficientJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class RevertSharingAgreementToDraftControllerTest extends BaseControllerTest {

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
    private SharingAgreementEntity publishedAgreement;

    @BeforeEach
    void setUp() {
        communityA = createCommunityRepository.create(CommunityMother.random().build());
        communityB = createCommunityRepository.create(CommunityMother.random().build());
        supplyA = createSupply(communityA);
        plantA = createPlant(supplyA);
        otherPlantInCommunityA = createPlant(createSupply(communityA));
        publishedAgreement = createAgreement(plantA, SharingAgreementStatus.PUBLISHED);
    }

    @Test
    void returnsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(post(url(plantA.getId(), publishedAgreement.getId()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsForbiddenForCommunityMember() throws Exception {
        String authHeader = loginAsCommunityMember(communityA.getId());

        mockMvc.perform(post(url(plantA.getId(), publishedAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsNotFoundForCrossCommunityAdmin() throws Exception {
        String authHeader = loginAsCommunityAdmin(communityB.getId());

        mockMvc.perform(post(url(plantA.getId(), publishedAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundWhenAgreementBelongsToAnotherPlantOfTheSameCommunity() throws Exception {
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(otherPlantInCommunityA.getId(), publishedAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsConflictWhenAgreementIsAlreadyDraft() throws Exception {
        SharingAgreementEntity draft = createAgreement(plantA, SharingAgreementStatus.DRAFT);
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(plantA.getId(), draft.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("SHARING_AGREEMENT_NOT_REVERTIBLE"));
    }

    @Test
    void returnsConflictWhenAgreementIsSuperseded() throws Exception {
        SharingAgreementEntity superseded = createAgreement(plantA, SharingAgreementStatus.SUPERSEDED);
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(plantA.getId(), superseded.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("SHARING_AGREEMENT_NOT_REVERTIBLE"));
    }

    @Test
    void draftAndSupersededRejectionsProduceDifferentMessages() throws Exception {
        SharingAgreementEntity draft = createAgreement(plantA, SharingAgreementStatus.DRAFT);
        SharingAgreementEntity superseded = createAgreement(plantA, SharingAgreementStatus.SUPERSEDED);
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        String draftMessage = mockMvc.perform(post(url(plantA.getId(), draft.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        String supersededMessage = mockMvc.perform(post(url(plantA.getId(), superseded.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();

        assertTrue(draftMessage.contains("DRAFT"));
        assertTrue(supersededMessage.contains("SUPERSEDED"));
        assertNotEquals(draftMessage, supersededMessage);
    }

    @Test
    void returnsConflictWhenAnyCoefficientIsApplied() throws Exception {
        Supply supplyB = createSupply(communityA);
        seedCoefficient(supplyA, plantA, publishedAgreement, null);
        seedCoefficient(supplyB, plantA, publishedAgreement, Instant.parse("2024-01-01T00:00:00Z"));
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(plantA.getId(), publishedAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("SHARING_AGREEMENT_HAS_APPLIED_COEFFICIENTS"));

        assertEquals(SharingAgreementStatus.PUBLISHED,
                sharingAgreementRepository.findById(publishedAgreement.getId()).orElseThrow().getStatus());
    }

    @Test
    void revertsPublishedAgreementWithOnlyPendingCoefficientsAndSetIsEditableAgain() throws Exception {
        UUID coefficientId = seedCoefficient(supplyA, plantA, publishedAgreement, null);
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(plantA.getId(), publishedAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(publishedAgreement.getId().toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        // No coefficient side effect: only the agreement's status changed.
        SupplyPartitionCoefficientEntity coefficient = supplyPartitionCoefficientJpaRepository.findById(coefficientId)
                .orElseThrow();
        assertNull(coefficient.getValidFrom());
        assertNull(coefficient.getValidTo());

        // The set is editable again: a draft-only operation (replacing coefficients) now succeeds.
        String replaceBody = """
                {
                  "coefficients": [
                    { "cups": "%s", "coefficient": 1 }
                  ]
                }
                """.formatted(supplyA.getCode());
        mockMvc.perform(put(coefficientsUrl(plantA.getId(), publishedAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replaceBody))
                .andDo(print())
                .andExpect(status().isOk());
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

    private UUID seedCoefficient(Supply supply, Plant plant, SharingAgreementEntity agreement, Instant validFrom) {
        SupplyEntity supplyEntity = supplyRepository.getReferenceById(supply.getId());
        PlantEntity plantEntity = plantRepository.getReferenceById(plant.getId());
        SharingAgreementEntity agreementReference = sharingAgreementRepository.getReferenceById(agreement.getId());

        SupplyPartitionCoefficientEntity coefficient = new SupplyPartitionCoefficientEntity();
        coefficient.setId(UUID.randomUUID());
        coefficient.setSupply(supplyEntity);
        coefficient.setPlant(plantEntity);
        coefficient.setSharingAgreement(agreementReference);
        coefficient.setCoefficient(BigDecimal.ONE);
        coefficient.setValidFrom(validFrom);
        coefficient.setCreatedAt(Instant.now());
        supplyPartitionCoefficientJpaRepository.save(coefficient);
        return coefficient.getId();
    }

    private String url(UUID plantId, UUID agreementId) {
        return "/api/v1/plants/" + plantId + "/sharing-agreements/" + agreementId + "/revert-to-draft";
    }

    private String coefficientsUrl(UUID plantId, UUID agreementId) {
        return "/api/v1/plants/" + plantId + "/sharing-agreements/" + agreementId + "/partition-coefficients";
    }
}
