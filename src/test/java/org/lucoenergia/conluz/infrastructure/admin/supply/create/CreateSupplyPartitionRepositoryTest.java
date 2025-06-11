package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.*;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyPartitionRepository;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.infrastructure.admin.supply.*;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
class CreateSupplyPartitionRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private CreateSupplyPartitionRepository repositoryDatabase;
    @Autowired
    private SupplyPartitionRepository supplyPartitionRepository;
    @Autowired
    private SupplyRepository supplyRepository;
    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldUpdateCoefficientSuccessfully() {
        // Arrange
        UserEntity user = UserMother.randomUserEntity();
        user = userRepository.save(user);

        SupplyEntity supplyEntity = SupplyEntityMother.random(user);
        supplyEntity = supplyRepository.save(supplyEntity);

        SharingAgreementEntity sharingAgreement = SharingAgreementEntityMother.random();
        sharingAgreementRepository.save(sharingAgreement);

        SupplyPartitionEntity supplyPartition = SupplyPartitionEntityMother.random(supplyEntity, sharingAgreement);
        supplyPartition.setCoefficient(0.5);
        supplyPartition = supplyPartitionRepository.save(supplyPartition);
        UUID id = supplyPartition.getId();

        Double newCoefficient = 0.75;

        // Act
        SupplyPartition result = repositoryDatabase.updateCoefficient(SupplyPartitionId.of(supplyPartition.getId()), newCoefficient);

        // Assert
        assertNotNull(result);
        assertEquals(id, result.getId());
        assertEquals(newCoefficient, result.getCoefficient());
    }

    @Test
    void shouldThrowExceptionWhenSupplyPartitionNotFound() {
        // Arrange
        UUID id = UUID.randomUUID();
        SupplyPartitionId supplyPartitionId = new SupplyPartitionId(id);
        Double newCoefficient = 0.75;

        // Act & Assert
        SupplyPartitionNotFoundException exception = assertThrows(SupplyPartitionNotFoundException.class, () ->
                repositoryDatabase.updateCoefficient(supplyPartitionId, newCoefficient)
        );

        assertEquals(supplyPartitionId, exception.getId());
    }

    @Test
    void shouldCreateSupplyPartitionSuccessfully() {
        // Arrange
        UserEntity user = UserMother.randomUserEntity();
        user = userRepository.save(user);

        SupplyEntity supplyEntity = SupplyEntityMother.random(user);
        supplyEntity = supplyRepository.save(supplyEntity);

        SharingAgreementEntity sharingAgreement = SharingAgreementEntityMother.random();
        sharingAgreementRepository.save(sharingAgreement);

        Double newCoefficient = 0.75;

        // Act
        SupplyPartition result = repositoryDatabase.create(SupplyCode.of(supplyEntity.getCode()), newCoefficient,
                SharingAgreementId.of(sharingAgreement.getId()));

        // Assert
        assertNotNull(result);
        assertEquals(newCoefficient, result.getCoefficient());
    }

    @Test
    void shouldThrowExceptionWhenSupplyNotFound() {
        // Arrange
        String supplyCodeValue = "SUPPLY-404";
        UUID sharingAgreementIdValue = UUID.randomUUID();
        Double newCoefficient = 0.75;

        SupplyCode supplyCode = SupplyCode.of(supplyCodeValue);
        SharingAgreementId sharingAgreementId = SharingAgreementId.of(sharingAgreementIdValue);

        // Act & Assert
        SupplyNotFoundException exception = assertThrows(SupplyNotFoundException.class, () ->
                repositoryDatabase.create(supplyCode, newCoefficient, sharingAgreementId)
        );

        assertEquals(supplyCode, exception.getCode());
    }

    @Test
    void shouldThrowExceptionWhenSharingAgreementNotFound() {
        // Arrange
        UserEntity user = UserMother.randomUserEntity();
        user = userRepository.save(user);

        SupplyEntity supplyEntity = SupplyEntityMother.random(user);
        supplyEntity = supplyRepository.save(supplyEntity);
        SupplyCode supplyCode = SupplyCode.of(supplyEntity.getCode());

        UUID sharingAgreementIdValue = UUID.randomUUID();
        SharingAgreementId sharingAgreementId = SharingAgreementId.of(sharingAgreementIdValue);

        Double newCoefficient = 0.85;

        // Act & Assert
        SharingAgreementNotFoundException exception = assertThrows(SharingAgreementNotFoundException.class, () ->
                repositoryDatabase.create(supplyCode, newCoefficient, sharingAgreementId)
        );

        assertEquals(sharingAgreementId, exception.getId());
    }
}