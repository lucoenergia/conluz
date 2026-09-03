package org.lucoenergia.conluz.infrastructure.production.sharingagreement.delete;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionCoefficientEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionCoefficientJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.delete.DeleteSharingAgreementRepositoryDatabase;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.sharingagreementfile.SharingAgreementFileEntity;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.sharingagreementfile.SharingAgreementFileRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;

@Transactional
class DeleteSharingAgreementRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private DeleteSharingAgreementRepositoryDatabase repository;
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
    @Autowired
    private SharingAgreementFileRepository sharingAgreementFileRepository;
    @Autowired
    private SupplyPartitionCoefficientJpaRepository coefficientJpaRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private PlantEntity persistPlant() {
        UserEntity user = UserMother.randomUserEntity();
        userRepository.save(user);
        SupplyEntity supply = supplyRepository.save(SupplyEntityMother.random(
                user, communityJpaRepository.getReferenceById(DEFAULT_COMMUNITY_ID)));
        return plantRepository.save(PlantMother.randomPlantEntity().withSupply(supply).build());
    }

    private SharingAgreementEntity persistAgreement(PlantEntity plant) {
        SharingAgreementEntity agreement = new SharingAgreementEntity();
        agreement.setId(UUID.randomUUID());
        agreement.setPlant(plant);
        agreement.setName("Test agreement " + UUID.randomUUID());
        agreement.setStatus(SharingAgreementStatus.DRAFT);
        agreement.setCreatedAt(Instant.now());
        agreement.setCreatedBy(null);
        return sharingAgreementRepository.save(agreement);
    }

    @Test
    void delete_removesTheAgreement() {
        PlantEntity plant = persistPlant();
        SharingAgreementEntity entity = persistAgreement(plant);

        repository.delete(plant.getId(), entity.getId());

        assertTrue(sharingAgreementRepository.findById(entity.getId()).isEmpty());
    }

    @Test
    void delete_removesTheAgreement_andItsFiles() {
        PlantEntity plant = persistPlant();
        SharingAgreementEntity entity = persistAgreement(plant);

        UserEntity uploader = UserMother.randomUserEntity();
        userRepository.save(uploader);

        SharingAgreementFileEntity file = new SharingAgreementFileEntity();
        file.setId(UUID.randomUUID());
        file.setSharingAgreement(entity);
        file.setFilename("distributor.csv");
        file.setContent("distributor,file,content".getBytes(StandardCharsets.UTF_8));
        file.setContentHash("hash");
        file.setUploadedAt(Instant.now());
        file.setUploadedBy(uploader.getId());
        sharingAgreementFileRepository.save(file);
        // Flush and clear so `file` is fully persisted and detached before the delete: the cascade is
        // handled by the DB, not Hibernate, and leaving `file` managed here would make Hibernate try to
        // cascade-check its (uncascaded) reference to the about-to-be-removed agreement and fail with a
        // TransientObjectException -- the same isolation a real, separate request would naturally have.
        entityManager.flush();
        entityManager.clear();

        repository.delete(plant.getId(), entity.getId());
        entityManager.flush();
        entityManager.clear();

        assertTrue(sharingAgreementRepository.findById(entity.getId()).isEmpty());
        assertTrue(sharingAgreementFileRepository.findById(file.getId()).isEmpty());
    }

    @Test
    void delete_removesTheAgreement_andItsCoefficients() {
        PlantEntity plant = persistPlant();
        SharingAgreementEntity entity = persistAgreement(plant);

        SupplyPartitionCoefficientEntity coefficient = new SupplyPartitionCoefficientEntity();
        coefficient.setId(UUID.randomUUID());
        coefficient.setSupply(plant.getSupply());
        coefficient.setPlant(plant);
        coefficient.setSharingAgreement(entity);
        coefficient.setCoefficient(BigDecimal.ONE);
        coefficient.setCreatedAt(Instant.now());
        coefficientJpaRepository.save(coefficient);
        entityManager.flush();
        entityManager.clear();

        repository.delete(plant.getId(), entity.getId());
        entityManager.flush();
        entityManager.clear();

        assertTrue(sharingAgreementRepository.findById(entity.getId()).isEmpty());
        assertTrue(coefficientJpaRepository.findById(coefficient.getId()).isEmpty());
    }

    @Test
    void delete_throwsNotFound_whenAgreementBelongsToAnotherPlant() {
        PlantEntity plantA = persistPlant();
        PlantEntity plantB = persistPlant();
        SharingAgreementEntity entity = persistAgreement(plantA);

        assertThrows(SharingAgreementNotFoundException.class,
                () -> repository.delete(plantB.getId(), entity.getId()));
        assertTrue(sharingAgreementRepository.findById(entity.getId()).isPresent());
    }

    @Test
    void delete_throwsNotFound_whenAgreementDoesNotExist() {
        PlantEntity plant = persistPlant();

        assertThrows(SharingAgreementNotFoundException.class,
                () -> repository.delete(plant.getId(), UUID.randomUUID()));
    }
}
