package org.lucoenergia.conluz.infrastructure.admin.user.delete;

import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.supply.SupplyOwnerNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.delete.DeleteUserService;
import org.lucoenergia.conluz.domain.admin.user.delete.UserOwnsSuppliesException;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.supply.create.BaseSupplyCreationRaceTest;
import org.springframework.beans.factory.annotation.Autowired;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deleting a user while a supply is being created for them (ACC-009). Both lock the user row, so one waits for the
 * other, and either order leaves a consistent state: no supply without an owner, and no unhandled error.
 */
class DeleteUserServiceIntegrationTest extends BaseSupplyCreationRaceTest {

    @Autowired
    private DeleteUserService deleteUserService;

    @Test
    @DisplayName("ACC-009 a user deleted while a supply is being created for them is refused once the supply exists")
    void deletionAfterAConcurrentSupplyCreationIsRefused() throws Exception {
        Community community = newCommunity();
        User owner = newMemberOf(community);
        String code = SupplyMother.randomCups();

        Throwable deletion = runWhileHeld(
                () -> createSupply(owner, community, code),
                () -> deleteUserService.delete(UserId.of(owner.getId())));

        assertThat(deletion).isInstanceOf(UserOwnsSuppliesException.class);
        assertThat(count("SELECT count(*) FROM users WHERE id = ?", owner.getId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM community_memberships WHERE user_id = ?", owner.getId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM supplies WHERE code = ? AND user_id = ?", code, owner.getId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("ACC-009 a supply created for a user while they are being deleted finds them gone, and is not created")
    void supplyCreationAfterAConcurrentDeletionFindsTheOwnerGone() throws Exception {
        Community community = newCommunity();
        User owner = newMemberOf(community);
        String code = SupplyMother.randomCups();

        Throwable creation = runWhileHeld(
                () -> deleteUserService.delete(UserId.of(owner.getId())),
                () -> createSupply(owner, community, code));

        assertThat(creation).isInstanceOf(SupplyOwnerNotFoundException.class);
        assertThat(count("SELECT count(*) FROM users WHERE id = ?", owner.getId())).isZero();
        assertThat(count("SELECT count(*) FROM supplies WHERE code = ?", code)).isZero();
        assertThat(count("SELECT count(*) FROM supplies s WHERE NOT EXISTS (SELECT 1 FROM community_memberships m "
                + "WHERE m.user_id = s.user_id AND m.community_id = s.community_id)")).isZero();
    }
}
