package org.lucoenergia.conluz.infrastructure.production.plant.delete;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.SaveSharingAgreementFileRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.SharingAgreementFile;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class DeletePlantControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/plants/{plantId}";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private GetPlantRepository getPlantRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CreateSupplyService createSupplyService;
    @Autowired
    private CreateSharingAgreementRepository createSharingAgreementRepository;
    @Autowired
    private SaveSharingAgreementFileRepository saveSharingAgreementFileRepository;
    @Autowired
    private SaveSupplyPartitionCoefficientRepository saveSupplyPartitionCoefficientRepository;
    @Autowired
    private SetHuaweiConfigurationRepository setHuaweiConfigurationRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    @Test
    void testDelete() throws Exception {

        // Create two users
        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        Supply supplyOne = SupplyMother.random().build();
        supplyOne = createSupplyRepository.create(supplyOne, UserId.of(userOne.getId()));
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo);
        Supply supplyTwo = SupplyMother.random().build();
        supplyTwo = createSupplyRepository.create(supplyTwo, UserId.of(userTwo.getId()));

        // Create three supplies
        Plant plantOne = PlantMother.random(supplyOne).withProviderCode("TS-456789").build();
        createPlantRepository.create(plantOne, SupplyId.of(supplyOne.getId()));
        Plant plantTwo = PlantMother.random(supplyOne).withProviderCode("TS-123456").build();
        plantTwo = createPlantRepository.create(plantTwo, SupplyId.of(supplyOne.getId()));
        Plant plantThree = PlantMother.random(supplyTwo).withProviderCode("TS-789456").build();
        createPlantRepository.create(plantThree, SupplyId.of(supplyTwo.getId()));

        // Login as an admin of the plant's community
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(delete(String.format("/api/v1/plants/%s", plantTwo.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        Assertions.assertTrue(getPlantRepository.findById(PlantId.of(plantTwo.getId())).isEmpty());
    }

    @Test
    void testWithUnknown() throws Exception {
        // A non-existent plant cannot be seen by the caller, so the access guard returns 404 before
        // the controller runs, avoiding plant-existence leakage.
        String authHeader = loginAsDefaultPlatformAdmin();

        final String plantId = UUID.randomUUID().toString();

        mockMvc.perform(delete("/api/v1/plants/" + plantId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithoutIdInPath() throws Exception {
        final String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(delete("/api/v1/plants")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithoutToken() throws Exception {

        mockMvc.perform(delete("/api/v1/plants")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testAuthenticatedUserWithoutAdminRoleCannotAccess() throws Exception {

        String authHeader = loginAsPartner();

        final String plantId = UUID.randomUUID().toString();

        // Test users endpoint
        mockMvc.perform(delete(String.format("/api/v1/plants/%s", plantId))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }

    @ParameterizedTest
    @EnumSource(SharingAgreementStatus.class)
    @DisplayName("PLT-001 deleting a plant a sharing agreement references is refused with 409 "
            + "PLANT_HAS_SHARING_AGREEMENTS, and nothing is deleted")
    void deletingAPlantWithASharingAgreementIsRefused(SharingAgreementStatus status) throws Exception {
        Community community = newCommunity();
        User owner = newMemberOf(community);
        Plant plant = createPlant(createSupply(owner, community));
        SharingAgreement agreement = createAgreement(plant, status);
        SharingAgreementFile file = saveSharingAgreementFileRepository.save(new SharingAgreementFile.Builder()
                .withId(UUID.randomUUID())
                .withSharingAgreementId(agreement.getId())
                .withFilename("distributor.txt")
                .withContent(new byte[]{1})
                .withContentHash("hash")
                .withUploadedAt(Instant.now())
                .withUploadedBy(owner.getId())
                .build(), plant.getId());

        startWithAnEmptyPersistenceContext();
        mockMvc.perform(delete(PATH, plant.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].code").value("PLANT_HAS_SHARING_AGREEMENTS"))
                .andExpect(jsonPath("$.errors[0].params.plantId").value(plant.getId().toString()))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());

        assertEquals(1, count("SELECT count(*) FROM plants WHERE id = ?", plant.getId()));
        assertEquals(1, count("SELECT count(*) FROM sharing_agreement WHERE id = ? AND plant_id = ? AND status = ?",
                agreement.getId(), plant.getId(), status.name()));
        assertEquals(1, count("SELECT count(*) FROM sharing_agreement_file WHERE id = ? AND sharing_agreement_id = ?",
                file.getId(), agreement.getId()));
    }

    /**
     * The coefficient sits in an agreement of another plant: an agreement of this plant would refuse the deletion
     * first, so this is the case in which the coefficient alone refuses it.
     */
    @Test
    @DisplayName("PLT-001 deleting a plant a partition coefficient references is refused with 409 "
            + "PLANT_HAS_PARTITION_COEFFICIENTS, and nothing is deleted")
    void deletingAPlantWithPartitionCoefficientsIsRefused() throws Exception {
        Community community = newCommunity();
        Plant plant = createPlant(createSupply(newMemberOf(community), community));
        Plant otherPlant = createPlant(createSupply(newMemberOf(community), community));
        SharingAgreement otherAgreement = createAgreement(otherPlant, SharingAgreementStatus.DRAFT);
        SupplyPartitionCoefficient coefficient = saveSupplyPartitionCoefficientRepository.save(
                new SupplyPartitionCoefficient.Builder()
                        .withId(UUID.randomUUID())
                        .withSupplyId(createSupply(newMemberOf(community), community).getId())
                        .withPlantId(plant.getId())
                        .withSharingAgreementId(otherAgreement.getId())
                        .withCoefficient(BigDecimal.ONE)
                        .withCreatedAt(Instant.now())
                        .build());

        startWithAnEmptyPersistenceContext();
        mockMvc.perform(delete(PATH, plant.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].code").value("PLANT_HAS_PARTITION_COEFFICIENTS"))
                .andExpect(jsonPath("$.errors[0].params.plantId").value(plant.getId().toString()))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());

        assertEquals(1, count("SELECT count(*) FROM plants WHERE id = ?", plant.getId()));
        assertEquals(1, count("SELECT count(*) FROM supply_partition_coefficient WHERE id = ? AND plant_id = ?",
                coefficient.getId(), plant.getId()));
    }

    @Test
    @DisplayName("PLT-001 deleting a plant deletes its Huawei configuration with it")
    void deletingAPlantDeletesItsHuaweiConfiguration() throws Exception {
        Community community = newCommunity();
        Plant plant = createPlant(createSupply(newMemberOf(community), community));
        setHuaweiConfigurationRepository.setHuaweiConfiguration(plant.getId(), new HuaweiConfig.Builder()
                .setUsername("testUsername")
                .setPassword("testPassword")
                .setBaseUrl(HuaweiConfig.DEFAULT_BASE_URL)
                .setEnabled(Boolean.TRUE)
                .build());
        assertEquals(1, count("SELECT count(*) FROM huawei_config WHERE plant_id = ?", plant.getId()));

        startWithAnEmptyPersistenceContext();
        mockMvc.perform(delete(PATH, plant.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.getId())))
                .andExpect(status().isOk());

        assertEquals(0, count("SELECT count(*) FROM plants WHERE id = ?", plant.getId()));
        assertEquals(0, count("SELECT count(*) FROM huawei_config WHERE plant_id = ?", plant.getId()));
    }

    @Test
    @DisplayName("PLT-001 deleting a plant nothing references deletes it")
    void deletingAPlantWithoutDependentsDeletesIt() throws Exception {
        Community community = newCommunity();
        Plant plant = createPlant(createSupply(newMemberOf(community), community));

        startWithAnEmptyPersistenceContext();
        mockMvc.perform(delete(PATH, plant.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.getId())))
                .andExpect(status().isOk());

        assertEquals(0, count("SELECT count(*) FROM plants WHERE id = ?", plant.getId()));
    }

    @Test
    void theContractDeclaresTheConflictsOfDeletingAPlant() throws Exception {
        String responses = "$.paths['/api/v1/plants/{plantId}'].delete.responses";
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(responses + "['409'].content['application/json'].examples['Has sharing agreements']"
                        + ".value.errors[0].code").value("PLANT_HAS_SHARING_AGREEMENTS"))
                .andExpect(jsonPath(responses + "['409'].content['application/json'].examples['Has sharing agreements']"
                        + ".value.errors[0].params.plantId").exists())
                .andExpect(jsonPath(responses + "['409'].content['application/json']"
                        + ".examples['Has partition coefficients'].value.errors[0].code")
                        .value("PLANT_HAS_PARTITION_COEFFICIENTS"))
                .andExpect(jsonPath(responses + "['409'].content['application/json']"
                        + ".examples['Has partition coefficients'].value.errors[0].params.plantId").exists());
    }

    private Community newCommunity() {
        return createCommunityRepository.create(CommunityMother.random().build());
    }

    private User newMemberOf(Community community) {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        createMembershipRepository.create(community.getId(), user.getId(), CommunityRole.COMMUNITY_MEMBER);
        return user;
    }

    private Supply createSupply(User owner, Community community) {
        return createSupplyService.create(SupplyMother.random(owner).build(), UserPersonalId.of(owner.getPersonalId()),
                community.getId());
    }

    private Plant createPlant(Supply supply) {
        return createPlantRepository.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId()));
    }

    private SharingAgreement createAgreement(Plant plant, SharingAgreementStatus status) {
        return createSharingAgreementRepository.create(new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withPlantId(plant.getId())
                .withName("Agreement " + UUID.randomUUID())
                .withStatus(status)
                .withInstalledPowerKw(BigDecimal.TEN)
                .withCreatedAt(Instant.now())
                .build());
    }

    /**
     * A request starts with an empty persistence context. Without this, the supply created above would still be
     * managed with its plants collection, which cascades and would keep the plant this test deletes.
     */
    private void startWithAnEmptyPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * Counts with plain SQL, after flushing what the request left in the persistence context: the test's transaction
     * is still open, and what is asserted is what the database holds.
     */
    private int count(String sql, Object... parameters) {
        entityManager.flush();
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, parameters);
        return count == null ? 0 : count;
    }
}
