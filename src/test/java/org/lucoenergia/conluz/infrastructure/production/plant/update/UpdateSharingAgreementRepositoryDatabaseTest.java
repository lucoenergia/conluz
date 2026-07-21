package org.lucoenergia.conluz.infrastructure.production.plant.update;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.plant.update.UpdateSharingAgreement;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;

@Transactional
class UpdateSharingAgreementRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private UpdateSharingAgreementRepositoryDatabase repository;
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

    private SharingAgreementEntity persistAgreement(PlantEntity plant) {
        SharingAgreementEntity agreement = new SharingAgreementEntity();
        agreement.setId(UUID.randomUUID());
        agreement.setPlant(plant);
        agreement.setName("Original name");
        agreement.setStatus(SharingAgreementStatus.DRAFT);
        agreement.setCreatedAt(Instant.now());
        agreement.setCreatedBy(null);
        return sharingAgreementRepository.save(agreement);
    }

    private UpdateSharingAgreement anUpdate(String name, String notes, BigDecimal installedPowerKw) {
        return new UpdateSharingAgreement.Builder()
                .withName(name)
                .withNotes(notes)
                .withInstalledPowerKw(installedPowerKw)
                .build();
    }

    @Test
    void update_changesOnlyDescriptiveFields() {
        PlantEntity plant = persistPlant();
        SharingAgreementEntity entity = persistAgreement(plant);

        SharingAgreement result = repository.update(plant.getId(), entity.getId(),
                anUpdate("New name", "New notes", BigDecimal.valueOf(9.5)));

        assertEquals("New name", result.getName());
        assertEquals("New notes", result.getNotes());
        assertEquals(0, BigDecimal.valueOf(9.5).compareTo(result.getInstalledPowerKw()));
        assertEquals(SharingAgreementStatus.DRAFT, result.getStatus());
        assertEquals(plant.getId(), result.getPlantId());
    }

    @Test
    void update_throwsNotFound_whenAgreementBelongsToAnotherPlant() {
        PlantEntity plantA = persistPlant();
        PlantEntity plantB = persistPlant();
        SharingAgreementEntity entity = persistAgreement(plantA);

        assertThrows(SharingAgreementNotFoundException.class,
                () -> repository.update(plantB.getId(), entity.getId(),
                        anUpdate("New name", "New notes", BigDecimal.ONE)));
    }

    @Test
    void update_throwsNotFound_whenAgreementDoesNotExist() {
        PlantEntity plant = persistPlant();

        assertThrows(SharingAgreementNotFoundException.class,
                () -> repository.update(plant.getId(), UUID.randomUUID(),
                        anUpdate("New name", "New notes", BigDecimal.ONE)));
    }
}
