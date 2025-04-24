package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.SupplyPartition;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.*;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class GetSupplyPartitionRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private GetSupplyPartitionRepositoryDatabase repository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SupplyRepository supplyRepository;
    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;
    @Autowired
    private SupplyPartitionRepository supplyPartitionRepository;

    @Test
    void shouldReturnSupplyPartitionWhenEntityExists() {
        UserEntity user = UserMother.randomUserEntity();
        user = userRepository.save(user);

        SupplyEntity supplyEntity = SupplyEntityMother.random(user);
        supplyEntity = supplyRepository.save(supplyEntity);
        SupplyId validSupplyId = SupplyId.of(supplyEntity.getId());

        SharingAgreementEntity sharingAgreement = SharingAgreementEntityMother.random();
        sharingAgreement = sharingAgreementRepository.save(sharingAgreement);
        SharingAgreementId validAgreementId = SharingAgreementId.of(sharingAgreement.getId());

        UUID partitionId = UUID.randomUUID();

        SupplyPartitionEntity entity = new SupplyPartitionEntity();
        entity.setId(partitionId);
        entity.setSupply(supplyEntity);
        entity.setSharingAgreement(sharingAgreement);
        entity.setCoefficient(0.5);
        supplyPartitionRepository.save(entity);

        Optional<SupplyPartition> result =
                repository.findBySupplyAndSharingAgreement(validSupplyId, validAgreementId);

        assertTrue(result.isPresent());
        assertEquals(partitionId, result.get().getId());
        assertEquals(0.5, result.get().getCoefficient());
    }

    @Test
    void shouldReturnEmptyWhenEntityDoesNotExist() {

        // Arrange
        UserEntity user = UserMother.randomUserEntity();
        user = userRepository.save(user);

        SupplyEntity supplyEntity = SupplyEntityMother.random(user);
        supplyEntity = supplyRepository.save(supplyEntity);
        SupplyId validSupplyId = SupplyId.of(supplyEntity.getId());

        SharingAgreementEntity sharingAgreement = SharingAgreementEntityMother.random();
        sharingAgreement = sharingAgreementRepository.save(sharingAgreement);
        SharingAgreementId validAgreementId = SharingAgreementId.of(sharingAgreement.getId());

        Optional<SupplyPartition> result =
                repository.findBySupplyAndSharingAgreement(validSupplyId, validAgreementId);

        assertTrue(result.isEmpty());
    }
}