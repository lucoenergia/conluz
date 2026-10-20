package org.lucoenergia.conluz.infrastructure.production.plant.delete;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.plant.delete.DeletePlantService;
import org.lucoenergia.conluz.domain.production.plant.delete.PlantHasSharingAgreementsException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.create.BaseSupplyCreationRaceTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deleting a plant while a sharing agreement is being created for it (PLT-001). The deletion locks the plant row
 * exclusively, so whichever of the two runs second waits for the first: a deletion that waits sees the agreement and
 * is refused rather than failing on the foreign key.
 */
class DeletePlantServiceIntegrationTest extends BaseSupplyCreationRaceTest {

    @Autowired
    private DeletePlantService deletePlantService;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private CreateSharingAgreementRepository createSharingAgreementRepository;
    @Autowired
    private EntityManager entityManager;

    private final List<UUID> plants = new CopyOnWriteArrayList<>();

    @AfterEach
    void deleteThePlants() {
        for (UUID plant : plants) {
            jdbcTemplate.update("DELETE FROM sharing_agreement WHERE plant_id = ?", plant);
            jdbcTemplate.update("DELETE FROM plants WHERE id = ?", plant);
        }
    }

    @Test
    @DisplayName("PLT-001 a plant deleted while a sharing agreement is being created for it is refused once the "
            + "agreement exists")
    void deletionAfterAConcurrentAgreementCreationIsRefused() throws Exception {
        Plant plant = newPlant();

        // Flushed, so the agreement's row is inserted, and its foreign key checked, while its transaction is still
        // open: otherwise nothing would reach the database before that transaction commits.
        Throwable deletion = runWhileHeld(
                () -> {
                    createAgreement(plant);
                    entityManager.flush();
                },
                () -> deletePlantService.delete(PlantId.of(plant.getId())));

        assertThat(deletion).isInstanceOf(PlantHasSharingAgreementsException.class);
        assertThat(count("SELECT count(*) FROM plants WHERE id = ?", plant.getId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM sharing_agreement WHERE plant_id = ?", plant.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("PLT-001 a sharing agreement created while its plant is being deleted fails once the plant is gone, "
            + "and the deletion stands")
    void agreementCreationAfterAConcurrentDeletionFails() throws Exception {
        Plant plant = newPlant();

        Throwable creation = runWhileHeld(
                () -> deletePlantService.delete(PlantId.of(plant.getId())),
                () -> createAgreement(plant));

        assertThat(creation).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("SELECT count(*) FROM plants WHERE id = ?", plant.getId())).isZero();
        assertThat(count("SELECT count(*) FROM sharing_agreement WHERE plant_id = ?", plant.getId())).isZero();
    }

    private Plant newPlant() {
        Community community = newCommunity();
        Supply supply = createSupply(newMemberOf(community), community, "ES" + UUID.randomUUID());
        Plant plant = createPlantRepository.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId()));
        plants.add(plant.getId());
        return plant;
    }

    private void createAgreement(Plant plant) {
        createSharingAgreementRepository.create(new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withPlantId(plant.getId())
                .withName("Agreement " + UUID.randomUUID())
                .withStatus(SharingAgreementStatus.DRAFT)
                .withInstalledPowerKw(BigDecimal.TEN)
                .withCreatedAt(Instant.now())
                .build());
    }
}
