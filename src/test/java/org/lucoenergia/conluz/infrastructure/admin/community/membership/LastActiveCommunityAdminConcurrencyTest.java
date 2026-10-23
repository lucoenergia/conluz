package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.MembershipLastActiveAdminException;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.DeleteMembershipService;
import org.lucoenergia.conluz.domain.admin.community.membership.UpdateMembershipRoleService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.db.LockWaitTestSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two admins of the same community removing each other at the same time, ordered with the lock wait of
 * {@link LockWaitTestSupport}: the first holds its transaction open until the second is blocked, or has finished
 * because nothing blocked it. Not transactional itself, so that each request commits as a request's own transaction
 * does. Users, communities and memberships are created the way production creates them, and deleted afterwards.
 */
class LastActiveCommunityAdminConcurrencyTest extends BaseIntegrationTest {

    @Autowired
    private UpdateMembershipRoleService updateMembershipRoleService;
    @Autowired
    private DeleteMembershipService deleteMembershipService;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> users = new CopyOnWriteArrayList<>();
    private final List<UUID> communities = new CopyOnWriteArrayList<>();

    @AfterEach
    void deleteTheData() {
        for (UUID user : users) {
            jdbcTemplate.update("DELETE FROM community_memberships WHERE user_id = ?", user);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", user);
        }
        for (UUID community : communities) {
            jdbcTemplate.update("DELETE FROM communities WHERE id = ?", community);
        }
    }

    /**
     * Without the community lock, each demotion would lock only its own target's rows, count two active admins, and
     * demote: the community would be left with none.
     */
    @Test
    @DisplayName("MEM-033 two active admins demoting each other at the same time: exactly one succeeds, and one "
            + "active admin remains")
    void twoAdminsDemotingEachOther_onlyOneSucceeds() throws Exception {
        Community community = newCommunity();
        User first = newAdminOf(community);
        User second = newAdminOf(community);

        Throwable secondOutcome = LockWaitTestSupport.runWhileHeld(transactionManager, jdbcTemplate,
                () -> updateMembershipRoleService.updateRole(community.getId(), second.getId(),
                        CommunityRole.COMMUNITY_MEMBER),
                () -> updateMembershipRoleService.updateRole(community.getId(), first.getId(),
                        CommunityRole.COMMUNITY_MEMBER));

        assertThat(secondOutcome).isInstanceOf(MembershipLastActiveAdminException.class);
        assertThat(activeAdmins(community)).isEqualTo(1);
        assertThat(role(community, first)).isEqualTo("COMMUNITY_ADMIN");
    }

    /**
     * Without the community lock, each deletion would lock only its own target's rows, count two active admins, and
     * delete: the community would be left with none.
     */
    @Test
    @DisplayName("MEM-033 two active admins removing each other at the same time: exactly one succeeds, and one "
            + "active admin remains")
    void twoAdminsRemovingEachOther_onlyOneSucceeds() throws Exception {
        Community community = newCommunity();
        User first = newAdminOf(community);
        User second = newAdminOf(community);

        Throwable secondOutcome = LockWaitTestSupport.runWhileHeld(transactionManager, jdbcTemplate,
                () -> deleteMembershipService.delete(community.getId(), second.getId()),
                () -> deleteMembershipService.delete(community.getId(), first.getId()));

        assertThat(secondOutcome).isInstanceOf(MembershipLastActiveAdminException.class);
        assertThat(activeAdmins(community)).isEqualTo(1);
        assertThat(role(community, first)).isEqualTo("COMMUNITY_ADMIN");
    }

    private Community newCommunity() {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        communities.add(community.getId());
        return community;
    }

    private User newAdminOf(Community community) {
        User user = UserMother.randomUser();
        user.setEnabled(true);
        createUserRepository.create(user);
        users.add(user.getId());
        createMembershipRepository.create(community.getId(), user.getId(), CommunityRole.COMMUNITY_ADMIN);
        return user;
    }

    private int activeAdmins(Community community) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM community_memberships m JOIN users u "
                + "ON u.id = m.user_id WHERE m.community_id = ? AND m.role = 'COMMUNITY_ADMIN' "
                + "AND m.accepted_at IS NOT NULL AND m.enabled AND u.enabled", Integer.class, community.getId());
    }

    private String role(Community community, User user) {
        return jdbcTemplate.queryForObject("SELECT role FROM community_memberships WHERE user_id = ? "
                + "AND community_id = ?", String.class, user.getId(), community.getId());
    }
}
