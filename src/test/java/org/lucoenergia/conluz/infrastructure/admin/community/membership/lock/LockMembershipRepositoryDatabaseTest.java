package org.lucoenergia.conluz.infrastructure.admin.community.membership.lock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.lock.LockMembershipRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Commits, since the lock must be seen from another transaction. The membership is created the way production
 * creates it, and deleted afterwards.
 */
class LockMembershipRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private LockMembershipRepository repository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID userId;
    private UUID communityId;

    @AfterEach
    void deleteTheMembership() {
        if (communityId != null) {
            jdbcTemplate.update("DELETE FROM community_memberships WHERE community_id = ?", communityId);
            jdbcTemplate.update("DELETE FROM communities WHERE id = ?", communityId);
        }
        if (userId != null) {
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    @Test
    void aLockOutsideATransaction_isRefused_becauseItWouldBeReleasedAtOnce() {
        UserId anyone = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> repository.lock(anyone, UUID.randomUUID()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void anUnknownMembership_isNotLocked() {
        Boolean locked = new TransactionTemplate(transactionManager)
                .execute(status -> repository.lock(UserId.of(UUID.randomUUID()), UUID.randomUUID()));

        assertThat(locked).isFalse();
    }

    @Test
    void aMembership_isLockedUntilTheEndOfTheTransaction() {
        createMembership();
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate other = new TransactionTemplate(transactionManager);
        other.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        outer.executeWithoutResult(status -> {
            assertThat(repository.lock(UserId.of(userId), communityId)).isTrue();

            assertThatThrownBy(() -> other.executeWithoutResult(inner -> lockWithoutWaiting()))
                    .isInstanceOf(DataAccessException.class);
        });

        other.executeWithoutResult(inner -> lockWithoutWaiting());
    }

    private void lockWithoutWaiting() {
        jdbcTemplate.queryForObject("SELECT id FROM community_memberships WHERE user_id = ? AND community_id = ? "
                + "FOR NO KEY UPDATE NOWAIT", UUID.class, userId, communityId);
    }

    private void createMembership() {
        User user = createUserRepository.create(UserMother.randomUser());
        userId = user.getId();
        communityId = createCommunityRepository.create(CommunityMother.random().build()).getId();
        createMembershipRepository.create(communityId, userId, CommunityRole.COMMUNITY_MEMBER);
    }
}
