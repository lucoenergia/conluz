package org.lucoenergia.conluz.infrastructure.admin.supply.delete;

import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.delete.SupplyHasPartitionCoefficientsException;
import org.lucoenergia.conluz.domain.admin.supply.delete.DeleteSupplyService;
import org.lucoenergia.conluz.domain.admin.supply.delete.SupplyHasPlantsException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantService;
import org.lucoenergia.conluz.domain.production.plant.update.UpdatePlantService;
import org.lucoenergia.conluz.domain.production.sharingagreement.MaterializeSharingAgreementCoefficientsService;
import org.lucoenergia.conluz.domain.production.sharingagreement.ResolvedCoefficientEntry;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.supply.create.BaseSupplyCreationRaceTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deleting a supply while a plant is being attached to it, or a partition coefficient written for it (SUP-001). The
 * deletion locks the supply row exclusively, and each writer locks it against deletion, so whichever of the two runs
 * second waits for the first: a deletion that waits sees the new row and is refused, and a writer that waits finds
 * the supply gone, rather than either failing on the foreign key. Also deletes a supply in the transaction that created it and its owner, where both are still
 * managed, and checks what was committed (#438).
 */
class DeleteSupplyServiceIntegrationTest extends BaseSupplyCreationRaceTest {

    @Autowired
    private DeleteSupplyService deleteSupplyService;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private CreatePlantService createPlantService;
    @Autowired
    private UpdatePlantService updatePlantService;
    @Autowired
    private CreateSharingAgreementService createSharingAgreementService;
    @Autowired
    private MaterializeSharingAgreementCoefficientsService materializeService;
    @Autowired
    private GetSupplyRepository getSupplyRepository;
    @Autowired
    private GetUserRepository getUserRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManager entityManager;

    private final List<UUID> supplies = new CopyOnWriteArrayList<>();

    @AfterEach
    void deleteThePlants() {
        for (UUID supply : supplies) {
            // Takes the agreements' coefficients with them
            jdbcTemplate.update("DELETE FROM sharing_agreement WHERE plant_id IN "
                    + "(SELECT id FROM plants WHERE supply_id = ?)", supply);
            jdbcTemplate.update("DELETE FROM plants WHERE supply_id = ?", supply);
        }
    }

    @Test
    @DisplayName("SUP-001 a supply deleted while a plant is being attached to it is refused once the plant exists")
    void deletionAfterAConcurrentPlantCreationIsRefused() throws Exception {
        Community community = newCommunity();
        Supply supply = createSupply(newMemberOf(community), community, SupplyMother.randomCups());
        supplies.add(supply.getId());

        Throwable deletion = runWhileHeld(
                () -> createPlantRepository.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId())),
                () -> deleteSupplyService.delete(SupplyId.of(supply.getId())));

        assertThat(deletion).isInstanceOf(SupplyHasPlantsException.class);
        assertThat(count("SELECT count(*) FROM supplies WHERE id = ?", supply.getId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM plants WHERE supply_id = ?", supply.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("SUP-001 a supply deleted while a plant is being created for it waits, and is refused once the plant "
            + "exists")
    void deletionAfterAConcurrentPlantCreationThroughTheServiceIsRefused() throws Exception {
        Community community = newCommunity();
        Supply supply = newSupply(community, newMemberOf(community));

        Throwable deletion = runWhileHeld(
                () -> createPlantService.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId())),
                () -> deleteSupplyService.delete(SupplyId.of(supply.getId())));

        assertThat(deletion).isInstanceOf(SupplyHasPlantsException.class);
        assertThat(count("SELECT count(*) FROM supplies WHERE id = ?", supply.getId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM plants WHERE supply_id = ?", supply.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("SUP-001 a plant created while its supply is being deleted waits, finds the supply gone, and is not "
            + "created")
    void plantCreationAfterAConcurrentDeletionFindsTheSupplyGone() throws Exception {
        Community community = newCommunity();
        Supply supply = newSupply(community, newMemberOf(community));

        Throwable creation = runWhileHeld(
                () -> deleteSupplyService.delete(SupplyId.of(supply.getId())),
                () -> createPlantService.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId())));

        assertThat(creation).isInstanceOf(SupplyNotFoundException.class);
        assertThat(count("SELECT count(*) FROM supplies WHERE id = ?", supply.getId())).isZero();
        assertThat(count("SELECT count(*) FROM plants WHERE supply_id = ?", supply.getId())).isZero();
    }

    @Test
    @DisplayName("SUP-001 a supply deleted while a plant is being moved to it waits, and is refused once the plant "
            + "references it")
    void deletionAfterAConcurrentPlantMoveIsRefused() throws Exception {
        Community community = newCommunity();
        User owner = newMemberOf(community);
        Plant plant = newPlant(newSupply(community, owner));
        Supply target = newSupply(community, owner);
        plant.setSupply(target);

        Throwable deletion = runWhileHeld(
                () -> updatePlantService.update(plant),
                () -> deleteSupplyService.delete(SupplyId.of(target.getId())));

        assertThat(deletion).isInstanceOf(SupplyHasPlantsException.class);
        assertThat(count("SELECT count(*) FROM supplies WHERE id = ?", target.getId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM plants WHERE id = ? AND supply_id = ?", plant.getId(), target.getId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("SUP-001 a plant moved to a supply while that supply is being deleted waits, finds the supply gone, "
            + "and stays where it was")
    void plantMoveAfterAConcurrentDeletionFindsTheSupplyGone() throws Exception {
        Community community = newCommunity();
        User owner = newMemberOf(community);
        Supply origin = newSupply(community, owner);
        Plant plant = newPlant(origin);
        Supply target = newSupply(community, owner);
        plant.setSupply(target);

        Throwable move = runWhileHeld(
                () -> deleteSupplyService.delete(SupplyId.of(target.getId())),
                () -> updatePlantService.update(plant));

        assertThat(move).isInstanceOf(SupplyNotFoundException.class);
        assertThat(count("SELECT count(*) FROM supplies WHERE id = ?", target.getId())).isZero();
        assertThat(count("SELECT count(*) FROM plants WHERE id = ? AND supply_id = ?", plant.getId(), origin.getId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("SUP-001 a supply deleted while partition coefficients are being written for it waits, and is refused "
            + "once they exist")
    void deletionAfterAConcurrentCoefficientMaterialisationIsRefused() throws Exception {
        Community community = newCommunity();
        User owner = newMemberOf(community);
        Plant plant = newPlant(newSupply(community, owner));
        SharingAgreement agreement = newDraftAgreement(plant, owner);
        Supply supply = newSupply(community, owner);

        Throwable deletion = runWhileHeld(
                () -> materializeService.replaceAllBySupplyId(plant.getId(), agreement.getId(),
                        List.of(new ResolvedCoefficientEntry(supply.getId(), BigDecimal.ONE))),
                () -> deleteSupplyService.delete(SupplyId.of(supply.getId())));

        assertThat(deletion).isInstanceOf(SupplyHasPartitionCoefficientsException.class);
        assertThat(count("SELECT count(*) FROM supplies WHERE id = ?", supply.getId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM supply_partition_coefficient WHERE supply_id = ?", supply.getId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("SUP-001 partition coefficients written for a supply while it is being deleted wait, find the supply "
            + "gone, and none is written")
    void coefficientMaterialisationAfterAConcurrentDeletionFindsTheSupplyGone() throws Exception {
        Community community = newCommunity();
        User owner = newMemberOf(community);
        Plant plant = newPlant(newSupply(community, owner));
        SharingAgreement agreement = newDraftAgreement(plant, owner);
        Supply supply = newSupply(community, owner);

        Throwable materialisation = runWhileHeld(
                () -> deleteSupplyService.delete(SupplyId.of(supply.getId())),
                () -> materializeService.replaceAllBySupplyId(plant.getId(), agreement.getId(),
                        List.of(new ResolvedCoefficientEntry(supply.getId(), BigDecimal.ONE))));

        assertThat(materialisation).isInstanceOf(SupplyNotFoundException.class);
        assertThat(count("SELECT count(*) FROM supplies WHERE id = ?", supply.getId())).isZero();
        assertThat(count("SELECT count(*) FROM supply_partition_coefficient WHERE sharing_agreement_id = ?",
                agreement.getId())).isZero();
    }

    @Test
    @DisplayName("SUP-001 a supply deleted in the transaction that created it and its owner stays deleted")
    void aSupplyDeletedWithItsOwnerLoadedStaysDeleted() {
        Community community = newCommunity();

        Supply supply = new TransactionTemplate(transactionManager).execute(status -> {
            User owner = newMemberOf(community);
            Supply created = createSupply(owner, community, SupplyMother.randomCups());
            assertThat(getUserRepository.findById(UserId.of(owner.getId()))).isPresent();
            assertThat(getSupplyRepository.findById(SupplyId.of(created.getId()))).isPresent();

            deleteSupplyService.delete(SupplyId.of(created.getId()));
            entityManager.flush();
            return created;
        });

        assertThat(count("SELECT count(*) FROM supplies WHERE id = ?", supply.getId())).isZero();
        assertThat(count("SELECT count(*) FROM users WHERE id = ?", supply.getUser().getId())).isEqualTo(1);
    }

    private Supply newSupply(Community community, User owner) {
        Supply supply = createSupply(owner, community, SupplyMother.randomCups());
        supplies.add(supply.getId());
        return supply;
    }

    private Plant newPlant(Supply supply) {
        return createPlantService.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId()));
    }

    private SharingAgreement newDraftAgreement(Plant plant, User author) {
        return createSharingAgreementService.create(plant.getId(), new CreateSharingAgreement.Builder()
                .withName("Agreement " + UUID.randomUUID())
                .withInstalledPowerKw(BigDecimal.TEN)
                .withCreatedBy(author.getId())
                .build());
    }
}
