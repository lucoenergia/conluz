package org.lucoenergia.conluz.infrastructure.admin.supply.transfer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.MembershipOwnsSuppliesException;
import org.lucoenergia.conluz.domain.admin.community.membership.DeleteMembershipService;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.SupplyOwnerNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.transfer.TransferSupplyOwnershipService;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.admin.supply.DatadisSupplyMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.create.BaseSupplyCreationRaceTest;
import org.lucoenergia.conluz.infrastructure.admin.supply.sync.DatadisSupplyUpdateStep;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Races a supply ownership transfer against a deletion of the new owner's membership (SUP-009), and against a
 * Datadis supply sync writing the same supply (SUP-012). Commits, since the race is between two transactions.
 */
class TransferSupplyOwnershipServiceIntegrationTest extends BaseSupplyCreationRaceTest {

    @Autowired
    private TransferSupplyOwnershipService transferSupplyOwnershipService;
    @Autowired
    private DeleteMembershipService deleteMembershipService;
    @Autowired
    private DatadisSupplyUpdateStep datadisSupplyUpdateStep;
    @Autowired
    private UpdateSupplyRepository updateSupplyRepository;
    @Autowired
    private GetSupplyRepository getSupplyRepository;

    private final List<UUID> transferredBy = new CopyOnWriteArrayList<>();

    /**
     * Runs before the base class deletes the users: a record names its users, who cannot be deleted before it.
     */
    @AfterEach
    void deleteTheRecords() {
        for (UUID user : transferredBy) {
            jdbcTemplate.update("DELETE FROM supply_ownership_transfers WHERE transferred_by = ?", user);
        }
    }

    @Test
    @DisplayName("SUP-009 a membership deleted while its user is being made a supply's owner waits, and is refused "
            + "once the user owns the supply")
    void aMembershipDeletedDuringATransferIsRefusedOnceTheUserOwnsTheSupply() throws Exception {
        Community community = newCommunity();
        User admin = admin(community);
        User next = newMemberOf(community);
        Supply supply = createSupply(newMemberOf(community), community, SupplyMother.randomCups());

        Throwable deletion = runWhileHeld(
                () -> transfer(supply, next, admin),
                () -> deleteMembershipService.delete(community.getId(), next.getId(), UserId.of(admin.getId())));

        assertThat(deletion).isInstanceOf(MembershipOwnsSuppliesException.class);
        assertThat(ownerOf(supply)).isEqualTo(next.getId());
        assertThat(count("SELECT count(*) FROM community_memberships WHERE user_id = ? AND community_id = ?",
                next.getId(), community.getId())).isOne();
        assertNoSupplyOwnedByANonMember();
    }

    @Test
    @DisplayName("SUP-009 a supply transferred to a user while their membership is being deleted waits, finds the "
            + "membership gone, and is not transferred")
    void aTransferDuringAMembershipDeletionFindsTheMembershipGone() throws Exception {
        Community community = newCommunity();
        User admin = admin(community);
        User next = newMemberOf(community);
        User owner = newMemberOf(community);
        Supply supply = createSupply(owner, community, SupplyMother.randomCups());

        Throwable transfer = runWhileHeld(
                () -> deleteMembershipService.delete(community.getId(), next.getId(), UserId.of(admin.getId())),
                () -> transfer(supply, next, admin));

        assertThat(transfer).isInstanceOf(SupplyOwnerNotFoundException.class);
        assertThat(ownerOf(supply)).isEqualTo(owner.getId());
        assertThat(count("SELECT count(*) FROM supply_ownership_transfers WHERE supply_id = ?", supply.getId()))
                .isZero();
        assertNoSupplyOwnedByANonMember();
    }

    @Test
    @DisplayName("SUP-012 a Datadis supply sync that read the supply before a transfer committed, and writes it "
            + "after, keeps the new owner")
    void aConcurrentDatadisSyncKeepsTheNewOwner() throws Exception {
        Community community = newCommunity();
        User admin = admin(community);
        User next = newMemberOf(community);
        // A blank address is one the sync fills from Datadis, so the sync writes the supply row itself
        User owner = newMemberOf(community);
        Supply supply = createSupplyService.create(SupplyMother.random(owner)
                        .withCode(SupplyMother.randomCups()).withAddress(" ").build(),
                UserPersonalId.of(owner.getPersonalId()), community.getId());

        Throwable sync = runWhileHeld(
                () -> transfer(supply, next, admin),
                () -> datadisSupplyUpdateStep.update(SupplyId.of(supply.getId()), DatadisSupplyMother
                        .random(supply.getCode()).withAddress("Calle Datadis 1").withDistributor("DISTRIBUTOR")
                        .build()));

        assertThat(sync).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT address FROM supplies WHERE id = ?", String.class,
                supply.getId())).isEqualTo("Calle Datadis 1");
        assertThat(ownerOf(supply)).isEqualTo(next.getId());
    }

    @Test
    @DisplayName("SUP-012 an update of a supply loaded before a transfer keeps the new owner")
    void anUpdateOfASupplyLoadedBeforeATransferKeepsTheNewOwner() {
        Community community = newCommunity();
        User admin = admin(community);
        User next = newMemberOf(community);
        Supply supply = createSupply(newMemberOf(community), community, SupplyMother.randomCups());
        Supply stale = getSupplyRepository.findById(SupplyId.of(supply.getId())).orElseThrow();

        transfer(supply, next, admin);
        stale.setAddress("Calle Nueva 2");
        updateSupplyRepository.update(stale);

        assertThat(jdbcTemplate.queryForObject("SELECT address FROM supplies WHERE id = ?", String.class,
                supply.getId())).isEqualTo("Calle Nueva 2");
        assertThat(ownerOf(supply)).isEqualTo(next.getId());
    }

    private User admin(Community community) {
        User admin = newMemberOf(community);
        transferredBy.add(admin.getId());
        return admin;
    }

    private void transfer(Supply supply, User next, User admin) {
        transferSupplyOwnershipService.transfer(SupplyId.of(supply.getId()),
                UserPersonalId.of(next.getPersonalId()), LocalDate.of(2025, 12, 1), UserId.of(admin.getId()));
    }

    private UUID ownerOf(Supply supply) {
        return jdbcTemplate.queryForObject("SELECT user_id FROM supplies WHERE id = ?", UUID.class, supply.getId());
    }

    private void assertNoSupplyOwnedByANonMember() {
        assertThat(count("SELECT count(*) FROM supplies s WHERE s.user_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM "
                + "community_memberships m WHERE m.user_id = s.user_id AND m.community_id = s.community_id)"))
                .isZero();
    }
}
