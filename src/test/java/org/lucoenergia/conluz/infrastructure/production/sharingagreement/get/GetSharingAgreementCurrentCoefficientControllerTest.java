package org.lucoenergia.conluz.infrastructure.production.sharingagreement.get;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers currentCoefficient on a sharing agreement's rows. Every fixture puts the supply in two
 * plants: with a single plant there is only one candidate, so nothing would prove the value is
 * selected by the agreement's plant rather than picked arbitrarily.
 */
@Transactional
class GetSharingAgreementCurrentCoefficientControllerTest extends BaseControllerTest {

    private static final Instant T0 = Instant.parse("2024-01-01T00:00:00Z");

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
    private SaveSupplyPartitionCoefficientRepository saveCoefficientRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void currentCoefficientIsTheOneFromTheAgreementsOwnPlantNotAnotherPlants() throws Exception {
        CommunityEntity community = persistCommunity();
        UserEntity user = persistUser();
        String authHeader = loginAsCommunityAdmin(community.getId());
        SupplyEntity supply = persistSupply(user, community, "ES0031300325733001AA0F");

        PlantEntity plantX = persistPlant(persistSupply(user, community, "ES0031300325733090AA0F"));
        PlantEntity plantY = persistPlant(persistSupply(user, community, "ES0031300325733091AA0F"));

        SharingAgreementEntity publishedInX = persistAgreement(plantX, SharingAgreementStatus.PUBLISHED, T0);
        SharingAgreementEntity publishedInY = persistAgreement(plantY, SharingAgreementStatus.PUBLISHED, T0);
        // Active in both plants, with different values, so the wrong plant is detectable.
        persistCoefficient(supply, plantX, publishedInX, BigDecimal.valueOf(0.250000), T0, null);
        persistCoefficient(supply, plantY, publishedInY, BigDecimal.valueOf(0.750000), T0, null);

        SharingAgreementEntity draftInX = persistAgreement(plantX, SharingAgreementStatus.DRAFT,
                Instant.parse("2025-01-01T00:00:00Z"));
        persistCoefficient(supply, plantX, draftInX, BigDecimal.valueOf(0.900000), null, null);

        mockMvc.perform(get(url(plantX.getId(), draftInX.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].currentCoefficient.coefficient").value(0.250000))
                .andExpect(jsonPath("$[0].currentCoefficient.validFrom").value("2024-01-01T00:00:00Z"))
                .andExpect(jsonPath("$[0].currentCoefficient.sharingAgreement.id").value(publishedInX.getId().toString()))
                .andExpect(jsonPath("$[0].currentCoefficient.sharingAgreement.status").value("PUBLISHED"));
    }

    @Test
    void currentCoefficientIsNullWhenTheSupplyHasNoActiveCoefficientInThisPlant() throws Exception {
        CommunityEntity community = persistCommunity();
        UserEntity user = persistUser();
        String authHeader = loginAsCommunityAdmin(community.getId());
        SupplyEntity supply = persistSupply(user, community, "ES0031300325733002AA0F");

        PlantEntity plantX = persistPlant(persistSupply(user, community, "ES0031300325733092AA0F"));
        PlantEntity plantY = persistPlant(persistSupply(user, community, "ES0031300325733093AA0F"));

        // Active in Y only. The draft under test is in X, so X has nothing in force.
        SharingAgreementEntity publishedInY = persistAgreement(plantY, SharingAgreementStatus.PUBLISHED, T0);
        persistCoefficient(supply, plantY, publishedInY, BigDecimal.valueOf(0.750000), T0, null);

        SharingAgreementEntity draftInX = persistAgreement(plantX, SharingAgreementStatus.DRAFT,
                Instant.parse("2025-01-01T00:00:00Z"));
        persistCoefficient(supply, plantX, draftInX, BigDecimal.valueOf(0.900000), null, null);

        mockMvc.perform(get(url(plantX.getId(), draftInX.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].currentCoefficient").doesNotExist());
    }

    @Test
    void currentCoefficientIgnoresAPendingCoefficientInTheSamePlant() throws Exception {
        CommunityEntity community = persistCommunity();
        UserEntity user = persistUser();
        String authHeader = loginAsCommunityAdmin(community.getId());
        SupplyEntity supply = persistSupply(user, community, "ES0031300325733003AA0F");
        PlantEntity plantX = persistPlant(persistSupply(user, community, "ES0031300325733094AA0F"));
        PlantEntity plantY = persistPlant(persistSupply(user, community, "ES0031300325733095AA0F"));
        persistCoefficient(supply, plantY,
                persistAgreement(plantY, SharingAgreementStatus.PUBLISHED, T0), BigDecimal.valueOf(0.5), T0, null);

        // The only row in X is pending: authored, never applied, so nothing is in force there.
        SharingAgreementEntity draftInX = persistAgreement(plantX, SharingAgreementStatus.DRAFT, T0);
        persistCoefficient(supply, plantX, draftInX, BigDecimal.valueOf(0.900000), null, null);

        mockMvc.perform(get(url(plantX.getId(), draftInX.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentCoefficient").doesNotExist());
    }

    /**
     * The N+1 guard for the agreement endpoint: resolving currentCoefficient must be one query for
     * the whole set. Comparing one supply against three is what proves that; an absolute count would
     * pass even if the lookup were per row.
     */
    @Test
    void resolvingCurrentCoefficientsCostsTheSameRegardlessOfSupplyCount() throws Exception {
        CommunityEntity community = persistCommunity();
        UserEntity user = persistUser();
        PlantEntity plant = persistPlant(persistSupply(user, community, "ES0031300325733096AA0F"));
        SharingAgreementEntity published = persistAgreement(plant, SharingAgreementStatus.PUBLISHED, T0);
        SharingAgreementEntity oneSupplyDraft = persistAgreement(plant, SharingAgreementStatus.DRAFT, T0);
        SharingAgreementEntity threeSupplyDraft = persistAgreement(plant, SharingAgreementStatus.DRAFT, T0);

        SupplyEntity only = persistSupply(user, community, "ES0031300325733010AA0F");
        persistCoefficient(only, plant, published, BigDecimal.valueOf(0.1), T0, null);
        persistCoefficient(only, plant, oneSupplyDraft, BigDecimal.valueOf(0.2), null, null);

        for (int i = 0; i < 3; i++) {
            SupplyEntity supply = persistSupply(user, community, "ES003130032573302" + i + "AA0F");
            persistCoefficient(supply, plant, published, BigDecimal.valueOf(0.1), T0, null);
            persistCoefficient(supply, plant, threeSupplyDraft, BigDecimal.valueOf(0.2), null, null);
        }

        long oneSupplyCost = countStatements(plant.getId(), oneSupplyDraft.getId(), 1);
        long threeSupplyCost = countStatements(plant.getId(), threeSupplyDraft.getId(), 3);

        assertEquals(oneSupplyCost, threeSupplyCost,
                "Resolving current coefficients for three supplies must not cost more queries than for one");
    }

    private long countStatements(UUID plantId, UUID agreementId, int expectedRows) throws Exception {
        entityManager.flush();
        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        long before = statistics.getPrepareStatementCount();
        mockMvc.perform(get(url(plantId, agreementId))
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(
                                supplyRepository.getReferenceById(
                                        plantRepository.getReferenceById(plantId).getSupply().getId())
                                        .getCommunity().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(expectedRows));
        return statistics.getPrepareStatementCount() - before;
    }

    private String url(UUID plantId, UUID agreementId) {
        return "/api/v1/plants/" + plantId + "/sharing-agreements/" + agreementId + "/partition-coefficients";
    }

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

    private SharingAgreementEntity persistAgreement(PlantEntity plant, SharingAgreementStatus status, Instant createdAt) {
        SharingAgreementEntity agreement = new SharingAgreementEntity();
        agreement.setId(UUID.randomUUID());
        agreement.setPlant(plant);
        agreement.setName("Test agreement " + UUID.randomUUID());
        agreement.setStatus(status);
        agreement.setCreatedAt(createdAt);
        agreement.setCreatedBy(null);
        return sharingAgreementRepository.save(agreement);
    }

    private void persistCoefficient(SupplyEntity supply, PlantEntity plant, SharingAgreementEntity agreement,
                                    BigDecimal coefficient, Instant validFrom, Instant validTo) {
        saveCoefficientRepository.save(new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supply.getId())
                .withPlantId(plant.getId())
                .withSharingAgreementId(agreement.getId())
                .withCoefficient(coefficient)
                .withValidFrom(validFrom)
                .withValidTo(validTo)
                .withCreatedAt(Instant.now())
                .build());
    }
}
