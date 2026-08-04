package org.lucoenergia.conluz.infrastructure.production.sharingagreement.revert;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementHasAppliedCoefficientsException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotRevertibleException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
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
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;

@Transactional
class RevertSharingAgreementToDraftRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private RevertSharingAgreementToDraftRepositoryDatabase repository;
    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;
    @Autowired
    private SaveSupplyPartitionCoefficientRepository saveSupplyPartitionCoefficientRepository;
    @Autowired
    private PlantRepository plantRepository;
    @Autowired
    private SupplyRepository supplyRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CommunityJpaRepository communityJpaRepository;

    private SupplyEntity persistSupply() {
        UserEntity user = UserMother.randomUserEntity();
        userRepository.save(user);
        return supplyRepository.save(SupplyEntityMother.random(
                user, communityJpaRepository.getReferenceById(DEFAULT_COMMUNITY_ID)));
    }

    private PlantEntity persistPlant(SupplyEntity supply) {
        return plantRepository.save(PlantMother.randomPlantEntity().withSupply(supply).build());
    }

    private SharingAgreementEntity persistAgreement(PlantEntity plant, SharingAgreementStatus status) {
        SharingAgreementEntity agreement = new SharingAgreementEntity();
        agreement.setId(UUID.randomUUID());
        agreement.setPlant(plant);
        agreement.setName("Test agreement " + UUID.randomUUID());
        agreement.setStatus(status);
        agreement.setCreatedAt(Instant.now());
        agreement.setCreatedBy(null);
        return sharingAgreementRepository.save(agreement);
    }

    private void persistCoefficient(UUID supplyId, UUID plantId, UUID sharingAgreementId, Instant validFrom) {
        saveSupplyPartitionCoefficientRepository.save(new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supplyId)
                .withPlantId(plantId)
                .withSharingAgreementId(sharingAgreementId)
                .withCoefficient(BigDecimal.ONE)
                .withValidFrom(validFrom)
                .withValidTo(null)
                .withCreatedAt(Instant.now())
                .build());
    }

    @Test
    void revertToDraft_transitionsPublishedToDraft_whenInert() {
        SupplyEntity supply = persistSupply();
        PlantEntity plant = persistPlant(supply);
        SharingAgreementEntity entity = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        persistCoefficient(supply.getId(), plant.getId(), entity.getId(), null);

        SharingAgreement result = repository.revertToDraft(plant.getId(), entity.getId());

        assertEquals(SharingAgreementStatus.DRAFT, result.getStatus());
        assertEquals(SharingAgreementStatus.DRAFT,
                sharingAgreementRepository.findById(entity.getId()).orElseThrow().getStatus());
    }

    @Test
    void revertToDraft_throwsHasAppliedCoefficients_whenAnyCoefficientIsApplied() {
        SupplyEntity supply1 = persistSupply();
        SupplyEntity supply2 = persistSupply();
        PlantEntity plant = persistPlant(supply1);
        SharingAgreementEntity entity = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        persistCoefficient(supply1.getId(), plant.getId(), entity.getId(), null);
        persistCoefficient(supply2.getId(), plant.getId(), entity.getId(), Instant.parse("2024-01-01T00:00:00Z"));

        assertThrows(SharingAgreementHasAppliedCoefficientsException.class,
                () -> repository.revertToDraft(plant.getId(), entity.getId()));
        assertEquals(SharingAgreementStatus.PUBLISHED,
                sharingAgreementRepository.findById(entity.getId()).orElseThrow().getStatus());
    }

    @Test
    void revertToDraft_throwsNotRevertible_whenAgreementIsDraft() {
        SupplyEntity supply = persistSupply();
        PlantEntity plant = persistPlant(supply);
        SharingAgreementEntity entity = persistAgreement(plant, SharingAgreementStatus.DRAFT);

        assertThrows(SharingAgreementNotRevertibleException.class,
                () -> repository.revertToDraft(plant.getId(), entity.getId()));
    }

    @Test
    void revertToDraft_throwsNotRevertible_whenAgreementIsSuperseded() {
        SupplyEntity supply = persistSupply();
        PlantEntity plant = persistPlant(supply);
        SharingAgreementEntity entity = persistAgreement(plant, SharingAgreementStatus.SUPERSEDED);

        assertThrows(SharingAgreementNotRevertibleException.class,
                () -> repository.revertToDraft(plant.getId(), entity.getId()));
    }

    @Test
    void revertToDraft_throwsNotFound_whenAgreementBelongsToAnotherPlant() {
        SupplyEntity supplyA = persistSupply();
        SupplyEntity supplyB = persistSupply();
        PlantEntity plantA = persistPlant(supplyA);
        PlantEntity plantB = persistPlant(supplyB);
        SharingAgreementEntity entity = persistAgreement(plantA, SharingAgreementStatus.PUBLISHED);

        assertThrows(SharingAgreementNotFoundException.class,
                () -> repository.revertToDraft(plantB.getId(), entity.getId()));
    }

    @Test
    void revertToDraft_throwsNotFound_whenAgreementDoesNotExist() {
        SupplyEntity supply = persistSupply();
        PlantEntity plant = persistPlant(supply);

        assertThrows(SharingAgreementNotFoundException.class,
                () -> repository.revertToDraft(plant.getId(), UUID.randomUUID()));
    }
}
