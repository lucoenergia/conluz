package org.lucoenergia.conluz.infrastructure.production.sharingagreement.publish;

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
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.sharingagreementfile.SharingAgreementFileEntity;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.sharingagreementfile.SharingAgreementFileRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.ContentHasher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    @Autowired
    private SharingAgreementFileRepository sharingAgreementFileRepository;

    private Community communityA;
    private Community communityB;
    private Plant plantA;
    private Supply supplyA;
    private Supply supplyB;
    private Supply supplyC;
    private Plant otherPlantInCommunityA;
    private SharingAgreementEntity draftAgreement;

    @BeforeEach
    void setUp() {
        communityA = createCommunityRepository.create(CommunityMother.random().build());
        communityB = createCommunityRepository.create(CommunityMother.random().build());
        supplyA = createSupply(communityA);
        supplyB = createSupply(communityA);
        supplyC = createSupply(communityA);
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
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("SHARING_AGREEMENT_NOT_DRAFT"));
    }

    @Test
    void returnsConflictWhenAgreementHasNoCoefficients() throws Exception {
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("SHARING_AGREEMENT_HAS_NO_COEFFICIENTS"));
    }

    @Test
    void returnsConflictWhenCoefficientSumIsLessThanOne() throws Exception {
        seedCoefficients(draftAgreement, new BigDecimal("0.300000"), new BigDecimal("0.300000"));
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("SHARING_AGREEMENT_COEFFICIENT_SUM_INVALID"))
                .andExpect(jsonPath("$.errors[0].params.actualSum").value("0.600000"));

        assertAgreementIsStillDraftWithUnchangedCoefficients(new BigDecimal("0.300000"), new BigDecimal("0.300000"));
    }

    @Test
    void returnsConflictWhenCoefficientSumIsGreaterThanOne() throws Exception {
        seedCoefficients(draftAgreement, new BigDecimal("0.700000"), new BigDecimal("0.700000"));
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("SHARING_AGREEMENT_COEFFICIENT_SUM_INVALID"))
                .andExpect(jsonPath("$.errors[0].params.actualSum").value("1.400000"));

        assertAgreementIsStillDraftWithUnchangedCoefficients(new BigDecimal("0.700000"), new BigDecimal("0.700000"));
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
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.file").value(nullValue()));
    }

    @Test
    void publishesDraftAgreementWithFile_returnsFileMetadata() throws Exception {
        seedCoefficient(supplyA, plantA, draftAgreement);
        User uploader = UserMother.randomUser();
        createUserRepository.create(uploader);
        byte[] content = "distributor content".getBytes(StandardCharsets.UTF_8);
        SharingAgreementFileEntity file = new SharingAgreementFileEntity();
        file.setId(UUID.randomUUID());
        file.setSharingAgreement(draftAgreement);
        file.setFilename("distributor.txt");
        file.setContent(content);
        file.setContentHash(ContentHasher.sha256Hex(content));
        file.setUploadedAt(Instant.now());
        file.setUploadedBy(uploader.getId());
        sharingAgreementFileRepository.save(file);
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.file.id").value(file.getId().toString()))
                .andExpect(jsonPath("$.file.filename").value("distributor.txt"));
    }

    @Test
    void publishesDraftAgreementWithCoefficientsSummingToOneOnlyAtFullScale() throws Exception {
        // 1/3 does not terminate; only the six-decimal-scale split sums to exactly 1 -- this would
        // fail under a naive double/float equality check.
        seedCoefficients(draftAgreement, new BigDecimal("0.333333"), new BigDecimal("0.333333"), new BigDecimal("0.333334"));
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

    /**
     * Seeds one coefficient row per value on {@code draftAgreement}/{@code plantA}, drawn from
     * {@code supplyA}/{@code supplyB}/{@code supplyC} in order. Supports up to three values.
     */
    private void seedCoefficients(SharingAgreementEntity agreement, BigDecimal... values) {
        List<Supply> supplies = List.of(supplyA, supplyB, supplyC);
        PlantEntity plantEntity = plantRepository.getReferenceById(plantA.getId());
        SharingAgreementEntity agreementReference = sharingAgreementRepository.getReferenceById(agreement.getId());
        for (int i = 0; i < values.length; i++) {
            SupplyEntity supplyEntity = supplyRepository.getReferenceById(supplies.get(i).getId());

            SupplyPartitionCoefficientEntity coefficient = new SupplyPartitionCoefficientEntity();
            coefficient.setId(UUID.randomUUID());
            coefficient.setSupply(supplyEntity);
            coefficient.setPlant(plantEntity);
            coefficient.setSharingAgreement(agreementReference);
            coefficient.setCoefficient(values[i]);
            coefficient.setValidFrom(null);
            coefficient.setCreatedAt(Instant.now());
            supplyPartitionCoefficientJpaRepository.save(coefficient);
        }
    }

    private void assertAgreementIsStillDraftWithUnchangedCoefficients(BigDecimal... expectedValues) {
        SharingAgreementEntity refreshed = sharingAgreementRepository.findById(draftAgreement.getId()).orElseThrow();
        assertEquals(SharingAgreementStatus.DRAFT, refreshed.getStatus());

        List<SupplyPartitionCoefficientEntity> coefficients = supplyPartitionCoefficientJpaRepository
                .findBySharingAgreementId(draftAgreement.getId());
        assertEquals(expectedValues.length, coefficients.size());
        for (BigDecimal expected : expectedValues) {
            assertTrue(coefficients.stream().anyMatch(c -> expected.compareTo(c.getCoefficient()) == 0));
        }
    }

    private String url(UUID plantId, UUID agreementId) {
        return "/api/v1/plants/" + plantId + "/sharing-agreements/" + agreementId + "/publish";
    }
}
