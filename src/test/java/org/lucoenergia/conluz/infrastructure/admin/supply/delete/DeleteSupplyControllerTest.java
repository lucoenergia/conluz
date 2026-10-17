package org.lucoenergia.conluz.infrastructure.admin.supply.delete;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class DeleteSupplyControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/supplies/{supplyId}";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipService createMembershipService;
    @Autowired
    private CreateSupplyService createSupplyService;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private CreateSharingAgreementRepository createSharingAgreementRepository;
    @Autowired
    private SaveSupplyPartitionCoefficientRepository saveSupplyPartitionCoefficientRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("SUP-001 deleting a supply deletes it with its contract, distributor and Shelly details, and keeps its "
            + "owner and the owner's membership")
    void deletingASupplyDeletesItWithItsDetails() throws Exception {
        Community community = newCommunity();
        User owner = newMemberOf(community);
        Supply supply = createSupply(owner, community);
        assertEquals(1, count("SELECT count(*) FROM supplies_contract WHERE supply_id = ?", supply.getId()));
        assertEquals(1, count("SELECT count(*) FROM supplies_distributor WHERE supply_id = ?", supply.getId()));
        assertEquals(1, count("SELECT count(*) FROM supplies_shelly WHERE supply_id = ?", supply.getId()));

        mockMvc.perform(delete(PATH, supply.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.getId())))
                .andExpect(status().isOk());

        assertEquals(0, count("SELECT count(*) FROM supplies WHERE id = ?", supply.getId()));
        assertEquals(0, count("SELECT count(*) FROM supplies_contract WHERE supply_id = ?", supply.getId()));
        assertEquals(0, count("SELECT count(*) FROM supplies_distributor WHERE supply_id = ?", supply.getId()));
        assertEquals(0, count("SELECT count(*) FROM supplies_shelly WHERE supply_id = ?", supply.getId()));
        assertEquals(1, count("SELECT count(*) FROM users WHERE id = ?", owner.getId()));
        assertEquals(1, count("SELECT count(*) FROM community_memberships WHERE user_id = ? AND community_id = ?",
                owner.getId(), community.getId()));
    }

    @Test
    @DisplayName("SUP-001 deleting a supply a plant is attached to is refused with 409 SUPPLY_HAS_PLANTS, and nothing is "
            + "deleted")
    void deletingASupplyWithAPlantIsRefused() throws Exception {
        Community community = newCommunity();
        Supply supply = createSupply(newMemberOf(community), community);
        Plant plant = createPlantRepository.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId()));

        mockMvc.perform(delete(PATH, supply.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].code").value("SUPPLY_HAS_PLANTS"))
                .andExpect(jsonPath("$.errors[0].params.supplyId").value(supply.getId().toString()))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());

        assertEquals(1, count("SELECT count(*) FROM supplies WHERE id = ?", supply.getId()));
        assertEquals(1, count("SELECT count(*) FROM supplies_contract WHERE supply_id = ?", supply.getId()));
        assertEquals(1, count("SELECT count(*) FROM plants WHERE id = ? AND supply_id = ?", plant.getId(),
                supply.getId()));
    }

    @Test
    @DisplayName("SUP-001 deleting a supply a sharing agreement holds coefficients for is refused with 409 "
            + "SUPPLY_HAS_PARTITION_COEFFICIENTS, and nothing is deleted")
    void deletingASupplyWithPartitionCoefficientsIsRefused() throws Exception {
        Community community = newCommunity();
        Supply plantSupply = createSupply(newMemberOf(community), community);
        Plant plant = createPlantRepository.create(PlantMother.random(plantSupply).build(),
                SupplyId.of(plantSupply.getId()));
        Supply supply = createSupply(newMemberOf(community), community);
        SharingAgreement agreement = createSharingAgreementRepository.create(new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withPlantId(plant.getId())
                .withName("Agreement " + UUID.randomUUID())
                .withStatus(SharingAgreementStatus.DRAFT)
                .withInstalledPowerKw(BigDecimal.TEN)
                .withCreatedAt(Instant.now())
                .build());
        SupplyPartitionCoefficient coefficient = saveSupplyPartitionCoefficientRepository.save(
                new SupplyPartitionCoefficient.Builder()
                        .withId(UUID.randomUUID())
                        .withSupplyId(supply.getId())
                        .withPlantId(plant.getId())
                        .withSharingAgreementId(agreement.getId())
                        .withCoefficient(BigDecimal.ONE)
                        .withCreatedAt(Instant.now())
                        .build());

        mockMvc.perform(delete(PATH, supply.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("SUPPLY_HAS_PARTITION_COEFFICIENTS"))
                .andExpect(jsonPath("$.errors[0].params.supplyId").value(supply.getId().toString()));

        assertEquals(1, count("SELECT count(*) FROM supplies WHERE id = ?", supply.getId()));
        assertEquals(1, count("SELECT count(*) FROM supply_partition_coefficient WHERE id = ? AND supply_id = ?",
                coefficient.getId(), supply.getId()));
    }

    @Test
    @DisplayName("SUP-001 once a community admin has deleted their supply, a member's account can be deleted")
    void deletingTheSupplyLetsItsOwnerBeDeleted() throws Exception {
        Community community = newCommunity();
        User owner = newMemberOf(community);
        Supply supply = createSupply(owner, community);
        String platformAdmin = loginAsDefaultPlatformAdmin();

        mockMvc.perform(delete("/api/v1/users/{userId}", owner.getId())
                        .header(HttpHeaders.AUTHORIZATION, platformAdmin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("USER_OWNS_SUPPLIES"));

        mockMvc.perform(delete(PATH, supply.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.getId())))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/users/{userId}", owner.getId())
                        .header(HttpHeaders.AUTHORIZATION, platformAdmin))
                .andExpect(status().isOk());

        assertEquals(0, count("SELECT count(*) FROM users WHERE id = ?", owner.getId()));
    }

    @Test
    void theOwnerMayNotDeleteTheirSupply() throws Exception {
        Community community = newCommunity();
        User owner = newMemberOf(community);
        Supply supply = createSupply(owner, community);

        mockMvc.perform(delete(PATH, supply.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginUser(owner)))
                .andExpect(status().isForbidden());

        assertEquals(1, count("SELECT count(*) FROM supplies WHERE id = ?", supply.getId()));
    }

    @Test
    void anAdminOfAnotherCommunityDoesNotSeeTheSupply() throws Exception {
        Community community = newCommunity();
        Supply supply = createSupply(newMemberOf(community), community);

        mockMvc.perform(delete(PATH, supply.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(newCommunity().getId())))
                .andExpect(status().isNotFound());

        assertEquals(1, count("SELECT count(*) FROM supplies WHERE id = ?", supply.getId()));
    }

    @Test
    void deletingAnUnknownSupplyIsReportedAsNotFound() throws Exception {
        mockMvc.perform(delete(PATH, UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(newCommunity().getId())))
                .andExpect(status().isNotFound());
    }

    /**
     * A platform admin has no access to a supply of a community they do not administer, and is answered like any
     * stranger (authorization policy, "What a platform admin does not get").
     */
    @Test
    void aPlatformAdminWhoDoesNotAdministerTheCommunityDoesNotSeeTheSupply() throws Exception {
        Community community = newCommunity();
        Supply supply = createSupply(newMemberOf(community), community);

        mockMvc.perform(delete(PATH, supply.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginAsDefaultPlatformAdmin()))
                .andExpect(status().isNotFound());

        assertEquals(1, count("SELECT count(*) FROM supplies WHERE id = ?", supply.getId()));
    }

    @Test
    void theContractDeclaresTheConflictsOfDeletingASupply() throws Exception {
        String responses = "$.paths['/api/v1/supplies/{supplyId}'].delete.responses";
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(responses + "['409'].content['application/json'].examples['Has plants']"
                        + ".value.errors[0].code").value("SUPPLY_HAS_PLANTS"))
                .andExpect(jsonPath(responses + "['409'].content['application/json']"
                        + ".examples['Has partition coefficients'].value.errors[0].code")
                        .value("SUPPLY_HAS_PARTITION_COEFFICIENTS"))
                .andExpect(jsonPath(responses + "['404']").exists());
    }

    private Community newCommunity() {
        return createCommunityRepository.create(CommunityMother.random().build());
    }

    private User newMemberOf(Community community) {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        createMembershipService.create(community.getId(), user.getId(), CommunityRole.COMMUNITY_MEMBER);
        return user;
    }

    private Supply createSupply(User owner, Community community) {
        return createSupplyService.create(SupplyMother.random(owner).build(), UserPersonalId.of(owner.getPersonalId()),
                community.getId());
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
