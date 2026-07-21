package org.lucoenergia.conluz.infrastructure.production.plant.get;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementStatus;
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
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;

@Transactional
class GetSharingAgreementRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private GetSharingAgreementRepositoryDatabase repository;
    @Autowired
    private SharingAgreementRepository sharingAgreementJpaRepository;
    @Autowired
    private PlantRepository plantRepository;
    @Autowired
    private SupplyRepository supplyRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CommunityJpaRepository communityJpaRepository;

    private PlantEntity persistPlant() {
        UserEntity user = UserMother.randomUserEntity();
        userRepository.save(user);
        SupplyEntity supply = supplyRepository.save(SupplyEntityMother.random(
                user, communityJpaRepository.getReferenceById(DEFAULT_COMMUNITY_ID)));
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
        return sharingAgreementJpaRepository.save(agreement);
    }

    // --- findCurrentPublishedAgreementIdByPlantId ---

    @Test
    void findCurrentPublishedAgreementIdByPlantId_returnsEmpty_whenNoAgreementsExist() {
        PlantEntity plant = persistPlant();

        Optional<UUID> result = repository.findCurrentPublishedAgreementIdByPlantId(plant.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    void findCurrentPublishedAgreementIdByPlantId_returnsLatestPublishedAgreementId() {
        PlantEntity plant = persistPlant();
        persistAgreement(plant, SharingAgreementStatus.PUBLISHED, Instant.now().minusSeconds(120));
        SharingAgreementEntity latestPublished = persistAgreement(plant, SharingAgreementStatus.PUBLISHED, Instant.now());
        // A newer DRAFT must not shadow the latest PUBLISHED one.
        persistAgreement(plant, SharingAgreementStatus.DRAFT, Instant.now().plusSeconds(60));

        Optional<UUID> result = repository.findCurrentPublishedAgreementIdByPlantId(plant.getId());

        assertTrue(result.isPresent());
        assertEquals(latestPublished.getId(), result.get());
    }

    // --- findById ---

    @Test
    void findById_returnsMappedAgreement_whenExists() {
        PlantEntity plant = persistPlant();
        SharingAgreementEntity entity = persistAgreement(plant, SharingAgreementStatus.DRAFT, Instant.now());

        Optional<SharingAgreement> result = repository.findById(entity.getId());

        assertTrue(result.isPresent());
        SharingAgreement agreement = result.get();
        assertEquals(entity.getId(), agreement.getId());
        assertEquals(plant.getId(), agreement.getPlantId());
        assertEquals(entity.getName(), agreement.getName());
        assertEquals(SharingAgreementStatus.DRAFT, agreement.getStatus());
    }

    @Test
    void findById_returnsEmpty_whenNotFound() {
        Optional<SharingAgreement> result = repository.findById(UUID.randomUUID());

        assertTrue(result.isEmpty());
    }

    // --- findByPlantId ---

    @Test
    void findByPlantId_withNullStatus_returnsAllAgreementsOrderedNewestFirst() {
        PlantEntity plant = persistPlant();
        SharingAgreementEntity older = persistAgreement(plant, SharingAgreementStatus.DRAFT, Instant.now().minusSeconds(60));
        SharingAgreementEntity newer = persistAgreement(plant, SharingAgreementStatus.PUBLISHED, Instant.now());

        List<SharingAgreement> result = repository.findByPlantId(plant.getId(), null);

        assertEquals(2, result.size());
        assertEquals(newer.getId(), result.get(0).getId());
        assertEquals(older.getId(), result.get(1).getId());
    }

    @Test
    void findByPlantId_withStatus_returnsOnlyMatchingAgreements() {
        PlantEntity plant = persistPlant();
        persistAgreement(plant, SharingAgreementStatus.DRAFT, Instant.now().minusSeconds(60));
        SharingAgreementEntity published = persistAgreement(plant, SharingAgreementStatus.PUBLISHED, Instant.now());

        List<SharingAgreement> result = repository.findByPlantId(plant.getId(), SharingAgreementStatus.PUBLISHED);

        assertEquals(1, result.size());
        assertEquals(published.getId(), result.get(0).getId());
    }

    @Test
    void findByPlantId_scopesResultsToTheGivenPlant() {
        PlantEntity plantA = persistPlant();
        PlantEntity plantB = persistPlant();
        SharingAgreementEntity agreementOfA = persistAgreement(plantA, SharingAgreementStatus.PUBLISHED, Instant.now());
        persistAgreement(plantB, SharingAgreementStatus.PUBLISHED, Instant.now());

        List<SharingAgreement> result = repository.findByPlantId(plantA.getId(), null);

        assertEquals(1, result.size());
        assertEquals(agreementOfA.getId(), result.get(0).getId());
    }
}
