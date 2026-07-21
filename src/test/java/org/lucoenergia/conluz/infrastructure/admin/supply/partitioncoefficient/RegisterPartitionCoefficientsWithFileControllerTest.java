package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class RegisterPartitionCoefficientsWithFileControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/supplies/partition-coefficients/import";
    private static final String FIXTURE = "fixtures/supplies/CAU_2024.txt";
    private static final String FIXTURE_SUM_OK = "fixtures/supplies/PARTITION_COEFFICIENTS_SUM_OK_2025.txt";
    private static final String FIXTURE_SUM_WARN = "fixtures/supplies/PARTITION_COEFFICIENTS_SUM_WARN_2025.txt";
    private static final String TXT_CONTENT_TYPE = "text/plain";
    private static final String EFFECTIVE_AT = "2024-01-01T00:00:00Z";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyService createSupplyService;
    @Autowired
    private GetCommunityRepository getCommunityRepository;
    @Autowired
    private SupplyRepository supplyJpaRepository;
    @Autowired
    private PlantRepository plantRepository;
    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;

    @Test
    void importsCoefficientsSuccessfully() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        createSupplyWithCode("ES0031300806333001KE0F");
        createSupplyWithCode("ES0031300326337001WS0F");

        MockMultipartFile file = loadFixture(FIXTURE, TXT_CONTENT_TYPE);

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("effectiveAt", EFFECTIVE_AT)
                        .param("communityId", DEFAULT_COMMUNITY_ID.toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").isArray())
                .andExpect(jsonPath("$.created", hasSize(2)))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[*].item", hasItem("ES0031300UNKNOWNCUPS00")));
    }

    @Test
    void handlesUnknownCupsAsError() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        createSupplyWithCode("ES0031300806333001KE0F");

        MockMultipartFile file = loadFixture(FIXTURE, TXT_CONTENT_TYPE);

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("effectiveAt", EFFECTIVE_AT)
                        .param("communityId", DEFAULT_COMMUNITY_ID.toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", hasSize(1)))
                .andExpect(jsonPath("$.errors", hasSize(2)));
    }

    @Test
    void communitySumEqualToOneReturnsNoWarning() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        createSupplyWithCode("ES0031300TESTFILEA010A");
        createSupplyWithCode("ES0031300TESTFILEA020B");
        createSupplyWithCode("ES0031300TESTFILEA030C");

        MockMultipartFile file = loadFixture(FIXTURE_SUM_OK, TXT_CONTENT_TYPE);

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("effectiveAt", EFFECTIVE_AT)
                        .param("communityId", DEFAULT_COMMUNITY_ID.toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", hasSize(3)))
                .andExpect(jsonPath("$.errors", hasSize(0)))
                .andExpect(jsonPath("$.communityCoefficientSumWarning").value(nullValue()));
    }

    @Test
    void communitySumDeviatingFromOneReturnsWarning() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        createSupplyWithCode("ES0031300TESTFILEB010A");
        createSupplyWithCode("ES0031300TESTFILEB020B");
        createSupplyWithCode("ES0031300TESTFILEB030C");

        MockMultipartFile file = loadFixture(FIXTURE_SUM_WARN, TXT_CONTENT_TYPE);

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("effectiveAt", EFFECTIVE_AT)
                        .param("communityId", DEFAULT_COMMUNITY_ID.toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", hasSize(3)))
                .andExpect(jsonPath("$.errors", hasSize(0)))
                .andExpect(jsonPath("$.communityCoefficientSumWarning")
                        .value("Community coefficient sum is 0.766666, expected 1"));
    }

    @Test
    void returnsBadRequestForWrongContentType() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        MockMultipartFile file = loadFixture(FIXTURE, "application/octet-stream");

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("effectiveAt", EFFECTIVE_AT)
                        .param("communityId", DEFAULT_COMMUNITY_ID.toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void returnsBadRequestForWrongExtension() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        ClassPathResource resource = new ClassPathResource(FIXTURE);
        MockMultipartFile file = new MockMultipartFile(
                "file", "CAU_2024.csv", TXT_CONTENT_TYPE,
                Files.readAllBytes(resource.getFile().toPath()));

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("effectiveAt", EFFECTIVE_AT)
                        .param("communityId", DEFAULT_COMMUNITY_ID.toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void returnsBadRequestForInvalidFilename() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        ClassPathResource resource = new ClassPathResource(FIXTURE);
        MockMultipartFile file = new MockMultipartFile(
                "file", "wrong_name.txt", TXT_CONTENT_TYPE,
                Files.readAllBytes(resource.getFile().toPath()));

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("effectiveAt", EFFECTIVE_AT)
                        .param("communityId", DEFAULT_COMMUNITY_ID.toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void returnsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(post(URL)
                        .param("effectiveAt", EFFECTIVE_AT)
                        .param("communityId", DEFAULT_COMMUNITY_ID.toString()))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void returnsForbiddenForNonAdminRole() throws Exception {
        String authHeader = loginAsPartner();
        MockMultipartFile file = loadFixture(FIXTURE, TXT_CONTENT_TYPE);

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("effectiveAt", EFFECTIVE_AT)
                        .param("communityId", DEFAULT_COMMUNITY_ID.toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }

    private Supply createSupplyWithCode(String code) {
        User user = UserMother.randomUser();
        createUserRepository.create(user);
        Supply supply = SupplyMother.random(user).withCode(code).build();
        Community community = getCommunityRepository.findAll().stream().findFirst().get();
        Supply created = createSupplyService.create(supply, UserPersonalId.of(user.getPersonalId()), community.getId());
        ensurePlantAndPublishedAgreement(created, community.getId());
        return created;
    }

    /**
     * The bulk import path resolves plant/agreement from each supply's community (interim
     * phase-2d inference), so the community needs a plant with a PUBLISHED agreement. Idempotent
     * per community: createSupplyWithCode is called multiple times per test, and a second plant for
     * the same community would violate the one-plant-per-community invariant the resolution relies on.
     */
    private void ensurePlantAndPublishedAgreement(Supply supply, UUID communityId) {
        PlantEntity plant = plantRepository.findBySupplyCommunityId(communityId)
                .orElseGet(() -> {
                    SupplyEntity supplyEntity = supplyJpaRepository.getReferenceById(supply.getId());
                    return plantRepository.save(PlantMother.randomPlantEntity().withSupply(supplyEntity).build());
                });

        boolean hasPublishedAgreement = sharingAgreementRepository
                .findFirstByPlantIdAndStatusOrderByCreatedAtDesc(plant.getId(), SharingAgreementStatus.PUBLISHED)
                .isPresent();
        if (!hasPublishedAgreement) {
            SharingAgreementEntity agreement = new SharingAgreementEntity();
            agreement.setId(UUID.randomUUID());
            agreement.setPlant(plant);
            agreement.setName("Test agreement " + UUID.randomUUID());
            agreement.setStatus(SharingAgreementStatus.PUBLISHED);
            agreement.setCreatedAt(Instant.now());
            agreement.setCreatedBy(null);
            sharingAgreementRepository.save(agreement);
        }
    }

    private MockMultipartFile loadFixture(String classPathLocation, String contentType) throws Exception {
        ClassPathResource resource = new ClassPathResource(classPathLocation);
        return new MockMultipartFile(
                "file",
                resource.getFilename(),
                contentType,
                Files.readAllBytes(resource.getFile().toPath()));
    }
}
