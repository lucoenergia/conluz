package org.lucoenergia.conluz.infrastructure.production.plant.delete;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfiguration;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationService;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.plant.delete.DeletePlantService;
import org.lucoenergia.conluz.domain.production.plant.delete.PlantHasSharingAgreementsException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementService;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.create.BaseSupplyCreationRaceTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deleting a plant while a sharing agreement or a Huawei configuration is being written for it (PLT-001). The
 * deletion locks the plant row exclusively, and each writer locks it against deletion, so whichever of the two runs
 * second waits for the first: a deletion that waits sees the agreement and is refused, or deletes the configuration
 * with the plant, and a writer that waits finds the plant gone, rather than either failing on the foreign key.
 */
class DeletePlantServiceIntegrationTest extends BaseSupplyCreationRaceTest {

    @Autowired
    private DeletePlantService deletePlantService;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private CreateSharingAgreementRepository createSharingAgreementRepository;
    @Autowired
    private CreateSharingAgreementService createSharingAgreementService;
    @Autowired
    private SetHuaweiConfigurationService setHuaweiConfigurationService;
    @Autowired
    private EntityManager entityManager;

    private final List<UUID> plants = new CopyOnWriteArrayList<>();

    @AfterEach
    void deleteThePlants() {
        for (UUID plant : plants) {
            jdbcTemplate.update("DELETE FROM sharing_agreement WHERE plant_id = ?", plant);
            jdbcTemplate.update("DELETE FROM huawei_config WHERE plant_id = ?", plant);
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
    @DisplayName("PLT-001 a plant deleted while a sharing agreement is being created for it waits, and is refused once "
            + "the agreement exists")
    void deletionAfterAConcurrentAgreementCreationThroughTheServiceIsRefused() throws Exception {
        Plant plant = newPlant();

        Throwable deletion = runWhileHeld(
                () -> createSharingAgreementService.create(plant.getId(), agreementRequest()),
                () -> deletePlantService.delete(PlantId.of(plant.getId())));

        assertThat(deletion).isInstanceOf(PlantHasSharingAgreementsException.class);
        assertThat(count("SELECT count(*) FROM plants WHERE id = ?", plant.getId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM sharing_agreement WHERE plant_id = ?", plant.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("PLT-001 a sharing agreement created while its plant is being deleted waits, finds the plant gone, "
            + "and is not created")
    void agreementCreationAfterAConcurrentDeletionFindsThePlantGone() throws Exception {
        Plant plant = newPlant();

        Throwable creation = runWhileHeld(
                () -> deletePlantService.delete(PlantId.of(plant.getId())),
                () -> createSharingAgreementService.create(plant.getId(), agreementRequest()));

        assertThat(creation).isInstanceOf(PlantNotFoundException.class);
        assertThat(count("SELECT count(*) FROM plants WHERE id = ?", plant.getId())).isZero();
        assertThat(count("SELECT count(*) FROM sharing_agreement WHERE plant_id = ?", plant.getId())).isZero();
    }

    @Test
    @DisplayName("PLT-001 a plant deleted while its Huawei configuration is being written waits, and is deleted with "
            + "the configuration")
    void deletionAfterAConcurrentHuaweiConfigurationDeletesIt() throws Exception {
        Plant plant = newPlant();

        Throwable deletion = runWhileHeld(
                () -> setHuaweiConfigurationService.setHuaweiConfiguration(plant.getId(), huaweiConfiguration()),
                () -> deletePlantService.delete(PlantId.of(plant.getId())));

        assertThat(deletion).isNull();
        assertThat(count("SELECT count(*) FROM plants WHERE id = ?", plant.getId())).isZero();
        assertThat(count("SELECT count(*) FROM huawei_config WHERE plant_id = ?", plant.getId())).isZero();
    }

    @Test
    @DisplayName("PLT-001 a Huawei configuration written while its plant is being deleted waits, finds the plant gone, "
            + "and is not written")
    void huaweiConfigurationAfterAConcurrentDeletionFindsThePlantGone() throws Exception {
        Plant plant = newPlant();

        Throwable configuration = runWhileHeld(
                () -> deletePlantService.delete(PlantId.of(plant.getId())),
                () -> setHuaweiConfigurationService.setHuaweiConfiguration(plant.getId(), huaweiConfiguration()));

        assertThat(configuration).isInstanceOf(PlantNotFoundException.class);
        assertThat(count("SELECT count(*) FROM plants WHERE id = ?", plant.getId())).isZero();
        assertThat(count("SELECT count(*) FROM huawei_config WHERE plant_id = ?", plant.getId())).isZero();
    }

    private static CreateSharingAgreement agreementRequest() {
        return new CreateSharingAgreement.Builder()
                .withName("Agreement " + UUID.randomUUID())
                .withInstalledPowerKw(BigDecimal.TEN)
                .build();
    }

    private static SetHuaweiConfiguration huaweiConfiguration() {
        return new SetHuaweiConfiguration.Builder()
                .withUsername("user")
                .withPassword("password")
                .withBaseUrl("https://fusionsolar.example.com/thirdData")
                .build();
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
