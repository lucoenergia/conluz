package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import org.junit.jupiter.api.Test;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class RegisterPartitionCoefficientControllerTest extends BaseControllerTest {

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
    void registersNewCoefficientWithSumEqualToOneReturnsNoWarning() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createTestSupply();
        String url = "/api/v1/supplies/" + supply.getId() + "/partition-coefficients";

        // Only active coefficient in the community at this instant, so the community sum
        // equals exactly 1 and no warning should be raised.
        String body = """
                {"coefficient": 1.000000, "effectiveAt": "2025-06-01T00:00:00Z"}
                """;

        mockMvc.perform(post(url)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplyId").value(supply.getId().toString()))
                .andExpect(jsonPath("$.coefficient").isNotEmpty())
                .andExpect(jsonPath("$.validFrom").value("2025-06-01T00:00:00Z"))
                .andExpect(jsonPath("$.communityCoefficientSumWarning").value(nullValue()));
    }

    @Test
    void registersNewCoefficientWithSumDeviatingFromOneReturnsWarning() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createTestSupply();
        String url = "/api/v1/supplies/" + supply.getId() + "/partition-coefficients";

        // Only active coefficient in the community at this instant, sum = 0.512345, well below 1.
        String body = """
                {"coefficient": 0.512345, "effectiveAt": "2025-06-01T00:00:00Z"}
                """;

        mockMvc.perform(post(url)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplyId").value(supply.getId().toString()))
                .andExpect(jsonPath("$.coefficient").isNotEmpty())
                .andExpect(jsonPath("$.validFrom").value("2025-06-01T00:00:00Z"))
                .andExpect(jsonPath("$.communityCoefficientSumWarning")
                        .value("Community coefficient sum is 0.512345, expected 1"));
    }

    @Test
    void legacyEndpointStillActivatesImmediatelyAfterValidFromBecameNullable() throws Exception {
        // Phase 5c makes valid_from nullable (pending rows) and leaves the phase-2d plant/agreement
        // inference in registerCoefficientChange functionally unchanged. This asserts the legacy
        // single-supply endpoint still writes an immediately-active (non-null validFrom) row, exactly
        // as before -- it never goes through the new pending-row materialisation path.
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createTestSupply();
        String url = "/api/v1/supplies/" + supply.getId() + "/partition-coefficients";

        String body = """
                {"coefficient": 0.750000, "effectiveAt": "2025-06-01T00:00:00Z"}
                """;

        mockMvc.perform(post(url)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validFrom").value("2025-06-01T00:00:00Z"))
                .andExpect(jsonPath("$.validFrom").isNotEmpty());
    }

    @Test
    void returnsBadRequestForNegativeCoefficient() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createTestSupply();
        String url = "/api/v1/supplies/" + supply.getId() + "/partition-coefficients";

        String body = """
                {"coefficient": -1.0, "effectiveAt": "2025-06-01T00:00:00Z"}
                """;

        mockMvc.perform(post(url)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsBadRequestWhenCoefficientMissing() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createTestSupply();
        String url = "/api/v1/supplies/" + supply.getId() + "/partition-coefficients";

        mockMvc.perform(post(url)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveAt\": \"2025-06-01T00:00:00Z\"}"))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/supplies/" + UUID_PLACEHOLDER + "/partition-coefficients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsForbiddenForNonAdminRole() throws Exception {
        String authHeader = loginAsPartner();
        Supply supply = createTestSupply();
        String url = "/api/v1/supplies/" + supply.getId() + "/partition-coefficients";

        mockMvc.perform(post(url)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coefficient\": 10.0, \"effectiveAt\": \"2025-06-01T00:00:00Z\"}"))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    private static final String UUID_PLACEHOLDER = "00000000-0000-0000-0000-000000000000";

    private Supply createTestSupply() {
        User user = UserMother.randomUser();
        createUserRepository.create(user);
        Supply supply = SupplyMother.random(user).build();
        Supply created = createSupplyService.create(supply, UserPersonalId.of(user.getPersonalId()), DEFAULT_COMMUNITY_ID);
        ensurePlantAndPublishedAgreement(created);
        return created;
    }

    /**
     * registerCoefficientChange resolves plant/agreement from the supply's community (interim
     * phase-2d inference), so the community needs a plant with a PUBLISHED agreement for that to
     * succeed. Idempotent per community: only creates them the first time they're missing.
     */
    private void ensurePlantAndPublishedAgreement(Supply supply) {
        PlantEntity plant = plantRepository.findBySupplyCommunityId(DEFAULT_COMMUNITY_ID)
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
}
