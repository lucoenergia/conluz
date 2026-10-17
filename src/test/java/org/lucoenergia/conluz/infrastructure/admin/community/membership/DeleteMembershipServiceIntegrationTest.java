package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.MembershipOwnsSuppliesException;
import org.lucoenergia.conluz.domain.admin.community.membership.DeleteMembershipService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.infrastructure.admin.supply.create.BaseSupplyCreationRaceTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deleting a membership while a supply is being created for its user in that community (MEM-029). Both lock the
 * user row, so one waits for the other, and either order leaves a consistent state: no supply whose owner is not a
 * member of its community, and no unhandled error.
 */
class DeleteMembershipServiceIntegrationTest extends BaseSupplyCreationRaceTest {

    @Autowired
    private DeleteMembershipService deleteMembershipService;

    @Test
    @DisplayName("MEM-029 a membership deleted while a supply is being created for its user in the community is "
            + "refused once the supply exists")
    void deletionAfterAConcurrentSupplyCreationIsRefused() throws Exception {
        Community community = newCommunity();
        User owner = newMemberOf(community);
        String code = "ES" + UUID.randomUUID();

        Throwable deletion = runWhileHeld(
                () -> createSupply(owner, community, code),
                () -> deleteMembershipService.delete(community.getId(), owner.getId()));

        assertThat(deletion).isInstanceOf(MembershipOwnsSuppliesException.class);
        assertThat(count("SELECT count(*) FROM community_memberships WHERE user_id = ? AND community_id = ?",
                owner.getId(), community.getId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM supplies WHERE code = ? AND user_id = ?", code, owner.getId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("MEM-029 a supply created for a user while their membership is being deleted finds it gone, and is "
            + "not created")
    void supplyCreationAfterAConcurrentDeletionFindsTheMembershipGone() throws Exception {
        Community community = newCommunity();
        User owner = newMemberOf(community);
        String code = "ES" + UUID.randomUUID();

        Throwable creation = runWhileHeld(
                () -> deleteMembershipService.delete(community.getId(), owner.getId()),
                () -> createSupply(owner, community, code));

        assertThat(creation).isInstanceOf(UserNotFoundException.class);
        assertThat(count("SELECT count(*) FROM community_memberships WHERE user_id = ? AND community_id = ?",
                owner.getId(), community.getId())).isZero();
        assertThat(count("SELECT count(*) FROM supplies WHERE code = ?", code)).isZero();
        assertThat(count("SELECT count(*) FROM supplies s WHERE NOT EXISTS (SELECT 1 FROM community_memberships m "
                + "WHERE m.user_id = s.user_id AND m.community_id = s.community_id)")).isZero();
    }
}
