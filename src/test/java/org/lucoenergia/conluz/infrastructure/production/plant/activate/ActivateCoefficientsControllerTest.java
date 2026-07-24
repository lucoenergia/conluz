package org.lucoenergia.conluz.infrastructure.production.plant.activate;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionCoefficientEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionCoefficientJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
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
class ActivateCoefficientsControllerTest extends BaseControllerTest {

    @Autowired
    private CommunityJpaRepository communityJpaRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SupplyRepository supplyRepository;
    @Autowired
    private PlantRepository plantRepository;
    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;
    @Autowired
    private SupplyPartitionCoefficientJpaRepository coefficientJpaRepository;

    private CommunityEntity communityA;
    private CommunityEntity communityB;
    private PlantEntity plantA;
    private PlantEntity otherPlantInCommunityA;
    private SupplyEntity supplyA;

    private CommunityEntity persistCommunity() {
        return communityJpaRepository.save(CommunityMother.randomEntity().build());
    }

    private SupplyEntity persistSupply(UserEntity user, CommunityEntity community) {
        return supplyRepository.save(SupplyEntityMother.random(user, community));
    }

    private PlantEntity persistPlant(SupplyEntity supply) {
        return plantRepository.save(PlantMother.randomPlantEntity().withSupply(supply).build());
    }

    private SharingAgreementEntity persistAgreement(PlantEntity plant, SharingAgreementStatus status) {
        SharingAgreementEntity agreement = new SharingAgreementEntity();
        agreement.setId(UUID.randomUUID());
        agreement.setPlant(plant);
        agreement.setName("Test agreement " + UUID.randomUUID());
        agreement.setStatus(status);
        agreement.setCreatedAt(Instant.now());
        agreement.setCreatedBy(null);
        return sharingAgreementRepository.save(agreement);
    }

    private SupplyPartitionCoefficientEntity persistCoefficient(SupplyEntity supply, PlantEntity plant,
                                                                  SharingAgreementEntity agreement, Instant validFrom) {
        SupplyPartitionCoefficientEntity entity = new SupplyPartitionCoefficientEntity();
        entity.setId(UUID.randomUUID());
        entity.setSupply(supplyRepository.getReferenceById(supply.getId()));
        entity.setPlant(plantRepository.getReferenceById(plant.getId()));
        entity.setSharingAgreement(sharingAgreementRepository.getReferenceById(agreement.getId()));
        entity.setCoefficient(BigDecimal.ONE);
        entity.setValidFrom(validFrom);
        entity.setCreatedAt(Instant.now());
        return coefficientJpaRepository.save(entity);
    }

    private void setUpBaseFixture() {
        communityA = persistCommunity();
        communityB = persistCommunity();
        UserEntity user = userRepository.save(UserMother.randomUserEntity());
        supplyA = persistSupply(user, communityA);
        plantA = persistPlant(supplyA);
        otherPlantInCommunityA = persistPlant(persistSupply(user, communityA));
    }

    private String url(UUID plantId, UUID agreementId) {
        return "/api/v1/plants/" + plantId + "/sharing-agreements/" + agreementId + "/partition-coefficients/activate";
    }

    private String body(String appliedOn, UUID... coefficientIds) {
        String ids = String.join(",", java.util.Arrays.stream(coefficientIds).map(id -> "\"" + id + "\"").toList());
        return """
                {"appliedOn": "%s", "coefficientIds": [%s]}
                """.formatted(appliedOn, ids);
    }

    @Test
    void returnsUnauthorizedWithoutToken() throws Exception {
        setUpBaseFixture();
        SharingAgreementEntity agreement = persistAgreement(plantA, SharingAgreementStatus.PUBLISHED);

        mockMvc.perform(post(url(plantA.getId(), agreement.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("2024-01-01", UUID.randomUUID())))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsForbiddenForCommunityMember() throws Exception {
        setUpBaseFixture();
        SharingAgreementEntity agreement = persistAgreement(plantA, SharingAgreementStatus.PUBLISHED);
        String authHeader = loginAsCommunityMember(communityA.getId());

        mockMvc.perform(post(url(plantA.getId(), agreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("2024-01-01", UUID.randomUUID())))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsNotFoundForCrossCommunityAdmin() throws Exception {
        setUpBaseFixture();
        SharingAgreementEntity agreement = persistAgreement(plantA, SharingAgreementStatus.PUBLISHED);
        String authHeader = loginAsCommunityAdmin(communityB.getId());

        mockMvc.perform(post(url(plantA.getId(), agreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("2024-01-01", UUID.randomUUID())))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundWhenAgreementBelongsToAnotherPlantOfTheSameCommunity() throws Exception {
        setUpBaseFixture();
        SharingAgreementEntity agreement = persistAgreement(plantA, SharingAgreementStatus.PUBLISHED);
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(otherPlantInCommunityA.getId(), agreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("2024-01-01", UUID.randomUUID())))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsConflictWhenAgreementIsDraft() throws Exception {
        setUpBaseFixture();
        SharingAgreementEntity draft = persistAgreement(plantA, SharingAgreementStatus.DRAFT);
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(plantA.getId(), draft.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("2024-01-01", UUID.randomUUID())))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("SHARING_AGREEMENT_NOT_PUBLISHED"));
    }

    @Test
    void returnsOkForSupersededAgreement() throws Exception {
        setUpBaseFixture();
        SharingAgreementEntity agreement = persistAgreement(plantA, SharingAgreementStatus.SUPERSEDED);
        SupplyPartitionCoefficientEntity coefficient = persistCoefficient(supplyA, plantA, agreement, null);
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(plantA.getId(), agreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("2024-01-01", coefficient.getId())))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    void returnsConflictWithTypedErrorForUnknownCoefficientId() throws Exception {
        setUpBaseFixture();
        SharingAgreementEntity agreement = persistAgreement(plantA, SharingAgreementStatus.PUBLISHED);
        String authHeader = loginAsCommunityAdmin(communityA.getId());
        UUID unknownId = UUID.randomUUID();

        mockMvc.perform(post(url(plantA.getId(), agreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("2024-01-01", unknownId)))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("SHARING_AGREEMENT_COEFFICIENT_NOT_IN_AGREEMENT"))
                .andExpect(jsonPath("$.errors[0].params.coefficientId").value(unknownId.toString()));
    }

    @Test
    void returnsBadRequestWhenAppliedOnMissing() throws Exception {
        setUpBaseFixture();
        SharingAgreementEntity agreement = persistAgreement(plantA, SharingAgreementStatus.PUBLISHED);
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(plantA.getId(), agreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coefficientIds\": [\"" + UUID.randomUUID() + "\"]}"))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsBadRequestWhenCoefficientIdsEmpty() throws Exception {
        setUpBaseFixture();
        SharingAgreementEntity agreement = persistAgreement(plantA, SharingAgreementStatus.PUBLISHED);
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(plantA.getId(), agreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appliedOn\": \"2024-01-01\", \"coefficientIds\": []}"))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void activatesPendingCoefficientAndIsIdempotentOnResend() throws Exception {
        setUpBaseFixture();
        SharingAgreementEntity agreement = persistAgreement(plantA, SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficientEntity coefficient = persistCoefficient(supplyA, plantA, agreement, null);
        String authHeader = loginAsCommunityAdmin(communityA.getId());
        String requestBody = body("2024-01-01", coefficient.getId());

        mockMvc.perform(post(url(plantA.getId(), agreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coefficients", org.hamcrest.Matchers.hasSize(1)))
                // application-test.properties configures conluz.time.zone.id=Europe/Madrid (CET, UTC+1
                // in January), so local midnight 2024-01-01 is 2023-12-31T23:00:00Z.
                .andExpect(jsonPath("$.coefficients[0].validFrom").value("2023-12-31T23:00:00Z"));

        // Idempotent: resending the identical batch is a no-op, not an error.
        mockMvc.perform(post(url(plantA.getId(), agreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coefficients", org.hamcrest.Matchers.hasSize(0)));
    }
}
