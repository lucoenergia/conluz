package org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class ReplacePartitionCoefficientsControllerTest extends BaseControllerTest {

    private static final String CUPS_1 = "ES0031300325733001FH0F";
    private static final String CUPS_2 = "ES0031300325733002FH0F";

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

    private CommunityEntity communityA;
    private CommunityEntity communityB;
    private PlantEntity plantA;
    private PlantEntity otherPlantInCommunityA;
    private SharingAgreementEntity draftAgreement;

    private CommunityEntity persistCommunity() {
        return communityJpaRepository.save(CommunityMother.randomEntity().build());
    }

    private UserEntity persistUser() {
        return userRepository.save(UserMother.randomUserEntity());
    }

    private SupplyEntity persistSupply(UserEntity user, CommunityEntity community, String cups) {
        SupplyEntity supply = SupplyEntityMother.random(user, community);
        supply.setCode(cups);
        return supplyRepository.save(supply);
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

    private void setUpBaseFixture() {
        communityA = persistCommunity();
        communityB = persistCommunity();
        UserEntity user = persistUser();
        SupplyEntity supply1 = persistSupply(user, communityA, CUPS_1);
        persistSupply(user, communityA, CUPS_2);
        plantA = persistPlant(supply1);
        otherPlantInCommunityA = persistPlant(persistSupply(user, communityA, "ES0031300325733009FH0F"));
        draftAgreement = persistAgreement(plantA, SharingAgreementStatus.DRAFT);
    }

    private String url(UUID plantId, UUID agreementId) {
        return "/api/v1/plants/" + plantId + "/sharing-agreements/" + agreementId + "/partition-coefficients";
    }

    private String bodyWithSumOne() {
        return """
                {"coefficients": [
                    {"cups": "%s", "coefficient": 0.500000},
                    {"cups": "%s", "coefficient": 0.500000}
                ]}
                """.formatted(CUPS_1, CUPS_2);
    }

    @Test
    void returnsUnauthorizedWithoutToken() throws Exception {
        setUpBaseFixture();

        mockMvc.perform(put(url(plantA.getId(), draftAgreement.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithSumOne()))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsForbiddenForCommunityMember() throws Exception {
        setUpBaseFixture();
        String authHeader = loginAsCommunityMember(communityA.getId());

        mockMvc.perform(put(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithSumOne()))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsNotFoundForCrossCommunityAdmin() throws Exception {
        setUpBaseFixture();
        String authHeader = loginAsCommunityAdmin(communityB.getId());

        mockMvc.perform(put(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithSumOne()))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundWhenAgreementBelongsToAnotherPlantOfTheSameCommunity() throws Exception {
        setUpBaseFixture();
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(put(url(otherPlantInCommunityA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithSumOne()))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsConflictWhenAgreementIsNotDraft() throws Exception {
        setUpBaseFixture();
        SharingAgreementEntity published = persistAgreement(plantA, SharingAgreementStatus.PUBLISHED);
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(put(url(plantA.getId(), published.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithSumOne()))
                .andDo(print())
                .andExpect(status().isConflict());
    }

    @Test
    void returnsBadRequestForDuplicateCups() throws Exception {
        setUpBaseFixture();
        String authHeader = loginAsCommunityAdmin(communityA.getId());
        String body = """
                {"coefficients": [
                    {"cups": "%s", "coefficient": 0.500000},
                    {"cups": "%s", "coefficient": 0.500000}
                ]}
                """.formatted(CUPS_1, CUPS_1);

        mockMvc.perform(put(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsBadRequestForEmptyCoefficientsList() throws Exception {
        setUpBaseFixture();
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(put(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coefficients\": []}"))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void replacesCoefficientsWithSumEqualToOneReturnsNoWarning() throws Exception {
        setUpBaseFixture();
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(put(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithSumOne()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coefficients", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.coefficients[0].validFrom").value(nullValue()))
                .andExpect(jsonPath("$.coefficientSumWarning").value(nullValue()));
    }

    @Test
    void replacesCoefficientsWithSumDeviatingFromOneReturnsWarning() throws Exception {
        setUpBaseFixture();
        String authHeader = loginAsCommunityAdmin(communityA.getId());
        String body = """
                {"coefficients": [
                    {"cups": "%s", "coefficient": 0.300000},
                    {"cups": "%s", "coefficient": 0.300000}
                ]}
                """.formatted(CUPS_1, CUPS_2);

        mockMvc.perform(put(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coefficientSumWarning")
                        .value("Coefficient set sum is 0.6, expected 1"));
    }
}
