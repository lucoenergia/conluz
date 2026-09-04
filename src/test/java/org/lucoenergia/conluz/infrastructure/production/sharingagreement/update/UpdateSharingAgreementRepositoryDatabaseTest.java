package org.lucoenergia.conluz.infrastructure.production.sharingagreement.update;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.update.UpdateSharingAgreement;
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
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.sharingagreementfile.SharingAgreementFileEntity;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.sharingagreementfile.SharingAgreementFileRepository;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.update.UpdateSharingAgreementRepositoryDatabase;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.ContentHasher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;

@Transactional
class UpdateSharingAgreementRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private UpdateSharingAgreementRepositoryDatabase repository;
    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;
    @Autowired
    private SharingAgreementFileRepository sharingAgreementFileRepository;
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

    private UserEntity persistUser() {
        UserEntity user = UserMother.randomUserEntity();
        return userRepository.save(user);
    }

    private SharingAgreementFileEntity persistFile(SharingAgreementEntity agreement, UserEntity uploader) {
        byte[] content = "distributor content".getBytes(StandardCharsets.UTF_8);
        SharingAgreementFileEntity file = new SharingAgreementFileEntity();
        file.setId(UUID.randomUUID());
        file.setSharingAgreement(agreement);
        file.setFilename("distributor.txt");
        file.setContent(content);
        file.setContentHash(ContentHasher.sha256Hex(content));
        file.setUploadedAt(Instant.now());
        file.setUploadedBy(uploader.getId());
        return sharingAgreementFileRepository.save(file);
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
    void update_returnsNullFile_whenNoFileUploaded() {
        PlantEntity plant = persistPlant();
        SharingAgreementEntity entity = persistAgreement(plant);

        SharingAgreement result = repository.update(plant.getId(), entity.getId(),
                anUpdate("New name", "New notes", BigDecimal.valueOf(9.5)));

        assertNull(result.getFile());
    }

    @Test
    void update_returnsFileSummary_whenFileAlreadyUploaded() {
        PlantEntity plant = persistPlant();
        SharingAgreementEntity entity = persistAgreement(plant);
        SharingAgreementFileEntity file = persistFile(entity, persistUser());

        SharingAgreement result = repository.update(plant.getId(), entity.getId(),
                anUpdate("New name", "New notes", BigDecimal.valueOf(9.5)));

        assertEquals(file.getId(), result.getFile().getId());
        assertEquals("distributor.txt", result.getFile().getFilename());
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
