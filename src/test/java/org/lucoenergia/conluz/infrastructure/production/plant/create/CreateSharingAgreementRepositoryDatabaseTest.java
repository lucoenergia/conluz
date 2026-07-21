package org.lucoenergia.conluz.infrastructure.production.plant.create;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
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
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;

@Transactional
class CreateSharingAgreementRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private CreateSharingAgreementRepositoryDatabase repository;
    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;
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

    @Test
    void create_persistsAgreementLinkedToPlant() {
        PlantEntity plant = persistPlant();
        UUID agreementId = UUID.randomUUID();
        UUID createdBy = UUID.randomUUID();
        SharingAgreement toCreate = new SharingAgreement.Builder()
                .withId(agreementId)
                .withPlantId(plant.getId())
                .withName("2024 winter distribution")
                .withNotes("initial")
                .withStatus(SharingAgreementStatus.DRAFT)
                .withInstalledPowerKw(BigDecimal.valueOf(12.5))
                .withCreatedAt(Instant.now())
                .withCreatedBy(createdBy)
                .build();

        SharingAgreement result = repository.create(toCreate);

        assertEquals(agreementId, result.getId());
        assertEquals(plant.getId(), result.getPlantId());
        assertEquals("2024 winter distribution", result.getName());
        assertEquals(SharingAgreementStatus.DRAFT, result.getStatus());
        assertEquals(0, BigDecimal.valueOf(12.5).compareTo(result.getInstalledPowerKw()));
        assertEquals(createdBy, result.getCreatedBy());
        assertTrue(sharingAgreementRepository.findById(agreementId).isPresent());
    }

    @Test
    void create_throwsPlantNotFound_whenPlantDoesNotExist() {
        SharingAgreement toCreate = new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withPlantId(UUID.randomUUID())
                .withName("Orphan agreement")
                .withStatus(SharingAgreementStatus.DRAFT)
                .withCreatedAt(Instant.now())
                .build();

        assertThrows(PlantNotFoundException.class, () -> repository.create(toCreate));
    }
}
