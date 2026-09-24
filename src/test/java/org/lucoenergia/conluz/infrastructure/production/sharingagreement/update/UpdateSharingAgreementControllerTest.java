package org.lucoenergia.conluz.infrastructure.production.sharingagreement.update;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
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
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    @Autowired
    private SharingAgreementFileRepository sharingAgreementFileRepository;
    @Autowired
    private SaveSupplyPartitionCoefficientRepository saveCoefficientRepository;
    @Autowired
    private GetSupplyPartitionCoefficientRepository getCoefficientRepository;

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
        mockMvc.perform(put(url(plantA.getId(), draftAgreement.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("New name", "5.5")))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsForbiddenForCommunityMember() throws Exception {
        String authHeader = loginAsCommunityMember(communityA.getId());

        mockMvc.perform(put(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("New name", "5.5")))
                .andDo(print())
                .andExpect(status().isForbidden());

        SharingAgreementEntity persisted = sharingAgreementRepository.findById(draftAgreement.getId()).orElseThrow();
        assertNull(persisted.getUpdatedAt());
        assertNull(persisted.getUpdatedBy());
    }

    @Test
    void returnsNotFoundForCrossCommunityAdmin() throws Exception {
        String authHeader = loginAsCommunityAdmin(communityB.getId());

        mockMvc.perform(put(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("New name", "5.5")))
                .andDo(print())
                .andExpect(status().isNotFound());

        SharingAgreementEntity persisted = sharingAgreementRepository.findById(draftAgreement.getId()).orElseThrow();
        assertNull(persisted.getUpdatedAt());
        assertNull(persisted.getUpdatedBy());
    }

    @Test
    void returnsNotFoundWhenAgreementBelongsToAnotherPlantOfTheSameCommunity() throws Exception {
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(put(url(otherPlantInCommunityA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("New name", "5.5")))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsBadRequestWhenInstalledPowerKwIsMissing() throws Exception {
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(put(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New name\"}"))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @EnumSource(value = SharingAgreementStatus.class, names = {"PUBLISHED", "SUPERSEDED"})
    void updatesSuccessfully_whenAgreementIsNotDraft(SharingAgreementStatus status) throws Exception {
        SharingAgreementEntity agreement = createAgreement(plantA, status);
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(put(url(plantA.getId(), agreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("New name", "5.5")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New name"))
                .andExpect(jsonPath("$.installedPowerKw").value(5.5))
                .andExpect(jsonPath("$.status").value(status.name()));

        SharingAgreementEntity persisted = sharingAgreementRepository.findById(agreement.getId()).orElseThrow();
        assertEquals(status, persisted.getStatus());
        assertEquals(0, BigDecimal.valueOf(5.5).compareTo(persisted.getInstalledPowerKw()));
    }

    @Test
    void updatesOnlyDescriptiveFields() throws Exception {
        String authHeader = loginAsCommunityAdmin(communityA.getId());
        Instant originalCreatedAt = draftAgreement.getCreatedAt();
        Instant before = Instant.now();

        mockMvc.perform(put(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Updated name", "9.75")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(draftAgreement.getId().toString()))
                .andExpect(jsonPath("$.plantId").value(plantA.getId().toString()))
                .andExpect(jsonPath("$.name").value("Updated name"))
                .andExpect(jsonPath("$.installedPowerKw").value(9.75))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.updatedBy").exists())
                .andExpect(jsonPath("$.file").value(nullValue()));

        SharingAgreementEntity persisted = sharingAgreementRepository.findById(draftAgreement.getId()).orElseThrow();
        assertEquals(plantA.getId(), persisted.getPlant().getId());
        assertEquals(originalCreatedAt, persisted.getCreatedAt());
        assertNull(persisted.getCreatedBy());
        assertEquals(SharingAgreementStatus.DRAFT, persisted.getStatus());
        assertNotNull(persisted.getUpdatedAt());
        assertFalse(persisted.getUpdatedAt().isBefore(before));
        assertNotNull(persisted.getUpdatedBy());
    }

    @Test
    void updatesSuccessfully_leavesPartitionCoefficientsUnchanged_whenAgreementIsPublished() throws Exception {
        Supply supply1 = createSupply(communityA);
        Supply supply2 = createSupply(communityA);
        SharingAgreementEntity published = createAgreement(plantA, SharingAgreementStatus.PUBLISHED);

        SupplyPartitionCoefficient coefficient1 = persistCoefficient(supply1, published,
                BigDecimal.valueOf(0.6), Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-06-01T00:00:00Z"));
        SupplyPartitionCoefficient coefficient2 = persistCoefficient(supply2, published,
                BigDecimal.valueOf(0.4), Instant.parse("2024-02-15T00:00:00Z"), null);

        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(put(url(plantA.getId(), published.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Updated name", "9.75")))
                .andDo(print())
                .andExpect(status().isOk());

        List<SupplyPartitionCoefficient> persistedCoefficients = getCoefficientRepository.findAllBySharingAgreementId(published.getId());
        assertEquals(2, persistedCoefficients.size());
        java.util.Map<UUID, SupplyPartitionCoefficient> byId = persistedCoefficients.stream()
                .collect(Collectors.toMap(SupplyPartitionCoefficient::getId, c -> c));

        SupplyPartitionCoefficient persisted1 = byId.get(coefficient1.getId());
        assertEquals(coefficient1.getSupplyId(), persisted1.getSupplyId());
        assertEquals(0, coefficient1.getCoefficient().compareTo(persisted1.getCoefficient()));
        assertEquals(coefficient1.getValidFrom(), persisted1.getValidFrom());
        assertEquals(coefficient1.getValidTo(), persisted1.getValidTo());

        SupplyPartitionCoefficient persisted2 = byId.get(coefficient2.getId());
        assertEquals(coefficient2.getSupplyId(), persisted2.getSupplyId());
        assertEquals(0, coefficient2.getCoefficient().compareTo(persisted2.getCoefficient()));
        assertEquals(coefficient2.getValidFrom(), persisted2.getValidFrom());
        assertNull(persisted2.getValidTo());
    }

    @Test
    void returnsFileMetadata_whenFileAlreadyUploaded() throws Exception {
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

        mockMvc.perform(put(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Updated name", "9.75")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.file.id").value(file.getId().toString()))
                .andExpect(jsonPath("$.file.filename").value("distributor.txt"));
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

    private Supply createSupply(Community community) {
        User owner = UserMother.randomUser();
        createUserRepository.create(owner);
        Supply supply = SupplyMother.random(owner).build();
        return createSupplyRepository.create(supply, UserId.of(owner.getId()), community.getId());
    }

    private SupplyPartitionCoefficient persistCoefficient(Supply supply, SharingAgreementEntity agreement,
                                                            BigDecimal coefficient, Instant validFrom, Instant validTo) {
        return saveCoefficientRepository.save(new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supply.getId())
                .withPlantId(plantA.getId())
                .withSharingAgreementId(agreement.getId())
                .withCoefficient(coefficient)
                .withValidFrom(validFrom)
                .withValidTo(validTo)
                .withCreatedAt(Instant.now())
                .build());
    }

    private String body(String name, String installedPowerKw) {
        return "{\"name\":\"" + name + "\",\"installedPowerKw\":" + installedPowerKw + "}";
    }

    private String url(UUID plantId, UUID agreementId) {
        return "/api/v1/plants/" + plantId + "/sharing-agreements/" + agreementId;
    }
}
