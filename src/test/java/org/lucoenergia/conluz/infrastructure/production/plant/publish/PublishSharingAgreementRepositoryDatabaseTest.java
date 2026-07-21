package org.lucoenergia.conluz.infrastructure.production.plant.publish;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementNotFoundException;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;

@Transactional
class PublishSharingAgreementRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private PublishSharingAgreementRepositoryDatabase repository;
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

    @Test
    void publish_transitionsDraftToPublished() {
        PlantEntity plant = persistPlant();
        SharingAgreementEntity entity = persistAgreement(plant, SharingAgreementStatus.DRAFT);

        SharingAgreement result = repository.publish(plant.getId(), entity.getId());

        assertEquals(SharingAgreementStatus.PUBLISHED, result.getStatus());
        assertEquals(SharingAgreementStatus.PUBLISHED,
                sharingAgreementRepository.findById(entity.getId()).orElseThrow().getStatus());
    }

    @Test
    void publish_throwsNotFound_whenAgreementBelongsToAnotherPlant() {
        PlantEntity plantA = persistPlant();
        PlantEntity plantB = persistPlant();
        SharingAgreementEntity entity = persistAgreement(plantA, SharingAgreementStatus.DRAFT);

        assertThrows(SharingAgreementNotFoundException.class,
                () -> repository.publish(plantB.getId(), entity.getId()));
    }

    @Test
    void publish_throwsNotFound_whenAgreementDoesNotExist() {
        PlantEntity plant = persistPlant();

        assertThrows(SharingAgreementNotFoundException.class,
                () -> repository.publish(plant.getId(), UUID.randomUUID()));
    }
}
