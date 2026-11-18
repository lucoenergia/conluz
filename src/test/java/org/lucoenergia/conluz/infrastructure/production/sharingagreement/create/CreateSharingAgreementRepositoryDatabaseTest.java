package org.lucoenergia.conluz.infrastructure.production.sharingagreement.create;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.domain.admin.community.CommunityMother.DEFAULT_COMMUNITY_ID;

@Transactional
class CreateSharingAgreementRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private CreateSharingAgreementRepository repository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
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
                .withNominalPowerKW(BigDecimal.valueOf(12.5))
                .withCreatedAt(Instant.now())
                .withCreatedBy(createdBy)
                .build();

        SharingAgreement result = repository.create(toCreate);

        assertEquals(agreementId, result.getId());
        assertEquals(plant.getId(), result.getPlantId());
        assertEquals("2024 winter distribution", result.getName());
        assertEquals(SharingAgreementStatus.DRAFT, result.getStatus());
        assertEquals(0, BigDecimal.valueOf(12.5).compareTo(result.getNominalPowerKW()));
        assertEquals(createdBy, result.getCreatedBy());
        assertTrue(sharingAgreementRepository.findById(agreementId).isPresent());
    }

    @Test
    void create_keepsTheDistributorFileSentOn() {
        User owner = UserMother.randomUser();
        createUserRepository.create(owner);
        Supply supply = createSupplyRepository.create(SupplyMother.random(owner).build(), UserId.of(owner.getId()),
                DEFAULT_COMMUNITY_ID);
        Plant plant = createPlantRepository.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId()));
        UUID agreementId = UUID.randomUUID();
        LocalDate sentOn = LocalDate.parse("2024-04-25");

        SharingAgreement result = repository.create(new SharingAgreement.Builder()
                .withId(agreementId)
                .withPlantId(plant.getId())
                .withName("Spring distribution")
                .withStatus(SharingAgreementStatus.PUBLISHED)
                .withNominalPowerKW(BigDecimal.TEN)
                .withCreatedAt(Instant.now())
                .withDistributorFileSentOn(sentOn)
                .build());

        assertEquals(sentOn, result.getDistributorFileSentOn());
        assertEquals(sentOn, sharingAgreementRepository.findById(agreementId).orElseThrow().getDistributorFileSentOn());
    }

    @Test
    void aFreshlyCreatedAgreementCannotHaveAFileYet() {
        PlantEntity plant = persistPlant();
        SharingAgreement toCreate = new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withPlantId(plant.getId())
                .withName("2024 winter distribution")
                .withStatus(SharingAgreementStatus.DRAFT)
                .withNominalPowerKW(BigDecimal.valueOf(12.5))
                .withCreatedAt(Instant.now())
                .build();

        SharingAgreement result = repository.create(toCreate);

        assertNull(result.getFile());
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
