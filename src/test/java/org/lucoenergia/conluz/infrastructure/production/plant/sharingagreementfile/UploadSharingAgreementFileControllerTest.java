package org.lucoenergia.conluz.infrastructure.production.plant.sharingagreementfile;

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
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class UploadSharingAgreementFileControllerTest extends BaseControllerTest {

    private static final String REGULATORY_CODE = "ES0031300325733001FH0FA000";
    private static final String FILENAME = REGULATORY_CODE + "_2024.txt";
    private static final String CUPS_1 = "ES0031300325733001FH0F";
    private static final String CUPS_2 = "ES0031300325733002FH0F";
    private static final String TXT_CONTENT_TYPE = "text/plain";

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

    private PlantEntity persistPlant(SupplyEntity supply, String regulatoryCode) {
        return plantRepository.save(PlantMother.randomPlantEntity()
                .withSupply(supply)
                .withRegulatoryCode(regulatoryCode)
                .build());
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
        plantA = persistPlant(supply1, REGULATORY_CODE);
        otherPlantInCommunityA = persistPlant(persistSupply(user, communityA, "ES0031300325733009FH0F"), "ES0031300325733009FH0FA000");
        draftAgreement = persistAgreement(plantA, SharingAgreementStatus.DRAFT);
    }

    private MockMultipartFile validFile() {
        String content = CUPS_1 + ";0,500000\n" + CUPS_2 + ";0,500000";
        return new MockMultipartFile("file", FILENAME, TXT_CONTENT_TYPE, content.getBytes(StandardCharsets.UTF_8));
    }

    private String url(UUID plantId, UUID agreementId) {
        return "/api/v1/plants/" + plantId + "/sharing-agreements/" + agreementId + "/file";
    }

    @Test
    void returnsUnauthorizedWithoutToken() throws Exception {
        setUpBaseFixture();

        mockMvc.perform(multipart(url(plantA.getId(), draftAgreement.getId())).file(validFile()))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsForbiddenForCommunityMember() throws Exception {
        setUpBaseFixture();
        String authHeader = loginAsCommunityMember(communityA.getId());

        mockMvc.perform(multipart(url(plantA.getId(), draftAgreement.getId())).file(validFile())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsNotFoundForCrossCommunityAdmin() throws Exception {
        setUpBaseFixture();
        String authHeader = loginAsCommunityAdmin(communityB.getId());

        mockMvc.perform(multipart(url(plantA.getId(), draftAgreement.getId())).file(validFile())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundWhenAgreementBelongsToAnotherPlantOfTheSameCommunity() throws Exception {
        setUpBaseFixture();
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(multipart(url(otherPlantInCommunityA.getId(), draftAgreement.getId())).file(validFile())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsConflictWhenAgreementIsNotDraft() throws Exception {
        setUpBaseFixture();
        SharingAgreementEntity published = persistAgreement(plantA, SharingAgreementStatus.PUBLISHED);
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(multipart(url(plantA.getId(), published.getId())).file(validFile())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isConflict());
    }

    @Test
    void returnsBadRequestForInvalidFile() throws Exception {
        setUpBaseFixture();
        String authHeader = loginAsCommunityAdmin(communityA.getId());
        MockMultipartFile invalid = new MockMultipartFile("file", FILENAME, TXT_CONTENT_TYPE,
                (CUPS_1 + ";0,600000\n" + CUPS_2 + ";0,600000").getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart(url(plantA.getId(), draftAgreement.getId())).file(invalid)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].code").value("DISTRIBUTOR_FILE_COEFFICIENT_SUM_INVALID"));
    }

    @Test
    void uploadsValidFileSuccessfully() throws Exception {
        setUpBaseFixture();
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(multipart(url(plantA.getId(), draftAgreement.getId())).file(validFile())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value(FILENAME))
                .andExpect(jsonPath("$.entriesMaterialized").value(2));
    }
}
