package org.lucoenergia.conluz.infrastructure.admin.supply.delete;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.delete.DeleteSupplyService;
import org.lucoenergia.conluz.domain.admin.supply.delete.SupplyHasPlantsException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.supply.create.BaseSupplyCreationRaceTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deleting a supply while a plant is being attached to it (SUP-001). The deletion locks the supply row exclusively,
 * so it waits for the plant's transaction and then sees the plant: it is refused rather than failing on the
 * foreign key. Also deletes a supply in the transaction that created it and its owner, where both are still
 * managed, and checks what was committed (#438).
 */
class DeleteSupplyServiceIntegrationTest extends BaseSupplyCreationRaceTest {

    @Autowired
    private DeleteSupplyService deleteSupplyService;
    @Autowired
    private CreatePlantRepository createPlantRepository;
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
            jdbcTemplate.update("DELETE FROM plants WHERE supply_id = ?", supply);
        }
    }

    @Test
    @DisplayName("SUP-001 a supply deleted while a plant is being attached to it is refused once the plant exists")
    void deletionAfterAConcurrentPlantCreationIsRefused() throws Exception {
        Community community = newCommunity();
        Supply supply = createSupply(newMemberOf(community), community, "ES" + UUID.randomUUID());
        supplies.add(supply.getId());

        Throwable deletion = runWhileHeld(
                () -> createPlantRepository.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId())),
                () -> deleteSupplyService.delete(SupplyId.of(supply.getId())));

        assertThat(deletion).isInstanceOf(SupplyHasPlantsException.class);
        assertThat(count("SELECT count(*) FROM supplies WHERE id = ?", supply.getId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM plants WHERE supply_id = ?", supply.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("SUP-001 a supply deleted in the transaction that created it and its owner stays deleted")
    void aSupplyDeletedWithItsOwnerLoadedStaysDeleted() {
        Community community = newCommunity();

        Supply supply = new TransactionTemplate(transactionManager).execute(status -> {
            User owner = newMemberOf(community);
            Supply created = createSupply(owner, community, "ES" + UUID.randomUUID());
            assertThat(getUserRepository.findById(UserId.of(owner.getId()))).isPresent();
            assertThat(getSupplyRepository.findById(SupplyId.of(created.getId()))).isPresent();

            deleteSupplyService.delete(SupplyId.of(created.getId()));
            entityManager.flush();
            return created;
        });

        assertThat(count("SELECT count(*) FROM supplies WHERE id = ?", supply.getId())).isZero();
        assertThat(count("SELECT count(*) FROM users WHERE id = ?", supply.getUser().getId())).isEqualTo(1);
    }
}
