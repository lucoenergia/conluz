package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetPartitionCoefficientControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyService createSupplyService;
    @Autowired
    private SaveSupplyPartitionCoefficientRepository partitionCoefficientRepository;
    @Autowired
    private GetCommunityRepository getCommunityRepository;
    @Autowired
    private SupplyRepository supplyJpaRepository;
    @Autowired
    private PlantRepository plantRepository;
    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;

    @Test
    void getHistoryReturnsAllPeriodsOrdered() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createTestSupply();
        SharingAgreementEntity agreement = ensurePlantAndPublishedAgreement(supply);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        persistCoefficient(supply.getId(), agreement, BigDecimal.valueOf(1.0), t0, t1);
        persistCoefficient(supply.getId(), agreement, BigDecimal.valueOf(2.0), t1, null);

        mockMvc.perform(get("/api/v1/supplies/" + supply.getId() + "/partition-coefficients")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].validFrom").value("2024-01-01T00:00:00Z"))
                .andExpect(jsonPath("$[1].validFrom").value("2025-01-01T00:00:00Z"));
    }

    @Test
    void getActiveReturnsRowWithNullValidTo() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createTestSupply();
        SharingAgreementEntity agreement = ensurePlantAndPublishedAgreement(supply);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        persistCoefficient(supply.getId(), agreement, BigDecimal.valueOf(1.0), t0, t1);
        persistCoefficient(supply.getId(), agreement, BigDecimal.valueOf(2.0), t1, null);

        mockMvc.perform(get("/api/v1/supplies/" + supply.getId() + "/partition-coefficients/active")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validTo").doesNotExist());
    }

    @Test
    void getAtTimestampReturnsCoefficientActiveAtThatInstant() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createTestSupply();
        SharingAgreementEntity agreement = ensurePlantAndPublishedAgreement(supply);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        persistCoefficient(supply.getId(), agreement, BigDecimal.valueOf(3.076300), t0, t1);
        persistCoefficient(supply.getId(), agreement, BigDecimal.valueOf(4.000000), t1, null);

        mockMvc.perform(get("/api/v1/supplies/" + supply.getId() + "/partition-coefficients/at")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("timestamp", "2024-06-15T12:00:00Z")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coefficient").value("3.0763"));
    }

    @Test
    void getAtTimestampReturns404WhenNoHistoryCoversTimestamp() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createTestSupply();
        // No coefficient seeded for this supply

        mockMvc.perform(get("/api/v1/supplies/" + supply.getId() + "/partition-coefficients/at")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("timestamp", "2024-06-15T12:00:00Z")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    private Supply createTestSupply() {
        User user = UserMother.randomUser();
        createUserRepository.create(user);
        Supply supply = SupplyMother.random(user).build();
        return createSupplyService.create(supply, UserPersonalId.of(user.getPersonalId()), DEFAULT_COMMUNITY_ID);
    }

    private SharingAgreementEntity ensurePlantAndPublishedAgreement(Supply supply) {
        SupplyEntity supplyEntity = supplyJpaRepository.getReferenceById(supply.getId());
        PlantEntity plant = plantRepository.save(PlantMother.randomPlantEntity().withSupply(supplyEntity).build());
        SharingAgreementEntity agreement = new SharingAgreementEntity();
        agreement.setId(UUID.randomUUID());
        agreement.setPlant(plant);
        agreement.setName("Test agreement " + UUID.randomUUID());
        agreement.setStatus(SharingAgreementStatus.PUBLISHED);
        agreement.setCreatedAt(Instant.now());
        agreement.setCreatedBy(null);
        return sharingAgreementRepository.save(agreement);
    }

    private void persistCoefficient(UUID supplyId, SharingAgreementEntity agreement,
                                    BigDecimal coefficient, Instant validFrom, Instant validTo) {
        partitionCoefficientRepository.save(new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supplyId)
                .withPlantId(agreement.getPlant().getId())
                .withSharingAgreementId(agreement.getId())
                .withCoefficient(coefficient)
                .withValidFrom(validFrom)
                .withValidTo(validTo)
                .withCreatedAt(Instant.now())
                .build());
    }
}
