package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.junit.jupiter.api.AfterEach;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.db.LockWaitTestSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Races a supply creation against a deletion of its owner, or of the owner's membership. Commits, since the race is
 * between two transactions: one runs first and holds its transaction open until the other is blocked on a lock, or
 * has finished because nothing blocked it, so the order of the two is certain. Users, communities, memberships and
 * supplies are created the way production creates them, and deleted afterwards.
 */
public abstract class BaseSupplyCreationRaceTest extends BaseIntegrationTest {

    @Autowired
    protected CreateSupplyService createSupplyService;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<UUID> users = new CopyOnWriteArrayList<>();
    private final List<UUID> communities = new CopyOnWriteArrayList<>();

    @AfterEach
    void deleteTheData() {
        for (UUID user : users) {
            jdbcTemplate.update("DELETE FROM supplies WHERE user_id = ?", user);
            jdbcTemplate.update("DELETE FROM community_memberships WHERE user_id = ?", user);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", user);
        }
        for (UUID community : communities) {
            jdbcTemplate.update("DELETE FROM communities WHERE id = ?", community);
        }
    }

    protected Community newCommunity() {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        communities.add(community.getId());
        return community;
    }

    protected User newMemberOf(Community... communities) {
        User user = UserMother.randomUser();
        user.setEnabled(true);
        createUserRepository.create(user);
        users.add(user.getId());
        for (Community community : communities) {
            createMembershipRepository.create(community.getId(), user.getId(), CommunityRole.COMMUNITY_MEMBER);
        }
        return user;
    }

    protected Supply createSupply(User owner, Community community, String code) {
        return createSupplyService.create(SupplyMother.random(owner).withCode(code).build(),
                UserPersonalId.of(owner.getPersonalId()), community.getId());
    }

    protected int count(String sql, Object... parameters) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, parameters);
        return count == null ? 0 : count;
    }

    /**
     * Runs {@code first} in a transaction and, while it is still open, starts {@code second} in another one. Commits
     * {@code first} once {@code second} is blocked on a lock or has finished, and returns how {@code second} ended:
     * {@code null} if it succeeded, or what it threw.
     */
    protected Throwable runWhileHeld(Runnable first, Runnable second) throws Exception {
        return LockWaitTestSupport.runWhileHeld(transactionManager, jdbcTemplate, first, second);
    }
}
