package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.delete.DeleteUserService;
import org.lucoenergia.conluz.domain.admin.user.disable.DisableUserRepository;
import org.lucoenergia.conluz.domain.admin.user.disable.DisableUserService;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.GetPlatformAdminsRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.LastPlatformAdminException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminAccessService;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.db.LockWaitTestSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The last two enabled platform admins removed at the same time, ordered with the lock wait of
 * {@link LockWaitTestSupport}: the first holds its transaction open until the second is blocked, or has finished
 * because nothing blocked it. Not transactional itself, so that each request commits as a request's own transaction
 * does.
 *
 * <p>Without the platform-admin lock, each flow would lock at most its own target's row, count two enabled platform
 * admins, and go on: the platform would be left with none.</p>
 */
class LastPlatformAdminConcurrencyTest extends BaseIntegrationTest {

    @Autowired
    private ManagePlatformAdminAccessService managePlatformAdminAccessService;
    @Autowired
    private DeleteUserService deleteUserService;
    @Autowired
    private DisableUserService disableUserService;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private ManagePlatformAdminRepository managePlatformAdminRepository;
    @Autowired
    private GetPlatformAdminsRepository getPlatformAdminsRepository;
    @Autowired
    private DisableUserRepository disableUserRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> users = new CopyOnWriteArrayList<>();
    private final List<EnabledPlatformAdmin> setAside = new ArrayList<>();

    /**
     * Platform admins committed by other test classes, such as the default one, would count too: they are disabled
     * for the test, so the two it creates are the only enabled ones, and enabled again afterwards.
     */
    @BeforeEach
    void setAsideTheOtherEnabledPlatformAdmins() {
        for (User admin : getPlatformAdminsRepository.findAll()) {
            if (admin.isEnabled()) {
                setAside.add(new EnabledPlatformAdmin(admin.getId(), jdbcTemplate.queryForObject(
                        "SELECT disabled_at FROM users WHERE id = ?", Timestamp.class, admin.getId())));
                disableUserRepository.disable(UserId.of(admin.getId()), Instant.now());
            }
        }
        assertThat(getPlatformAdminsRepository.countPlatformAdmins()).isZero();
    }

    @AfterEach
    void deleteTheDataAndRestoreThePlatformAdmins() {
        for (UUID user : users) {
            jdbcTemplate.update("DELETE FROM community_memberships WHERE user_id = ?", user);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", user);
        }
        // Restored as they were, disabled_at included, which no repository method writes back
        for (EnabledPlatformAdmin admin : setAside) {
            jdbcTemplate.update("UPDATE users SET enabled = true, disabled_at = ? WHERE id = ?", admin.disabledAt(),
                    admin.id());
        }
    }

    @Test
    @DisplayName("ACC-013 two revokes of the last two enabled platform admins at the same time: exactly one succeeds, "
            + "and one enabled platform admin remains")
    void twoRevokes_onlyOneSucceeds() throws Exception {
        User first = newEnabledPlatformAdmin();
        User second = newEnabledPlatformAdmin();

        Throwable secondOutcome = LockWaitTestSupport.runWhileHeld(transactionManager, jdbcTemplate,
                () -> managePlatformAdminAccessService.revoke(UserId.of(first.getId())),
                () -> managePlatformAdminAccessService.revoke(UserId.of(second.getId())));

        assertThat(secondOutcome).isInstanceOf(LastPlatformAdminException.class);
        assertThat(getPlatformAdminsRepository.countPlatformAdmins()).isEqualTo(1);
        assertThat(isEnabledPlatformAdmin(second)).isTrue();
    }

    @Test
    @DisplayName("ACC-009 ACC-013 two deletions of the last two enabled platform admins at the same time: exactly one "
            + "succeeds, and one enabled platform admin remains")
    void twoDeletions_onlyOneSucceeds() throws Exception {
        User first = newEnabledPlatformAdmin();
        User second = newEnabledPlatformAdmin();

        Throwable secondOutcome = LockWaitTestSupport.runWhileHeld(transactionManager, jdbcTemplate,
                () -> deleteUserService.delete(UserId.of(first.getId())),
                () -> deleteUserService.delete(UserId.of(second.getId())));

        assertThat(secondOutcome).isInstanceOf(LastPlatformAdminException.class);
        assertThat(getPlatformAdminsRepository.countPlatformAdmins()).isEqualTo(1);
        assertThat(isEnabledPlatformAdmin(second)).isTrue();
    }

    @Test
    @DisplayName("ACC-008 ACC-013 two disablings of the last two enabled platform admins at the same time: exactly one "
            + "succeeds, and one enabled platform admin remains")
    void twoDisablings_onlyOneSucceeds() throws Exception {
        User first = newEnabledPlatformAdmin();
        User second = newEnabledPlatformAdmin();

        Throwable secondOutcome = LockWaitTestSupport.runWhileHeld(transactionManager, jdbcTemplate,
                () -> disableUserService.disable(UserId.of(first.getId())),
                () -> disableUserService.disable(UserId.of(second.getId())));

        assertThat(secondOutcome).isInstanceOf(LastPlatformAdminException.class);
        assertThat(getPlatformAdminsRepository.countPlatformAdmins()).isEqualTo(1);
        assertThat(isEnabledPlatformAdmin(second)).isTrue();
    }

    @Test
    @DisplayName("ACC-009 ACC-013 a revoke and a deletion of the last two enabled platform admins at the same time: "
            + "exactly one succeeds, and one enabled platform admin remains")
    void aRevokeRacingADeletion_onlyOneSucceeds() throws Exception {
        User first = newEnabledPlatformAdmin();
        User second = newEnabledPlatformAdmin();

        Throwable secondOutcome = LockWaitTestSupport.runWhileHeld(transactionManager, jdbcTemplate,
                () -> managePlatformAdminAccessService.revoke(UserId.of(first.getId())),
                () -> deleteUserService.delete(UserId.of(second.getId())));

        assertThat(secondOutcome).isInstanceOf(LastPlatformAdminException.class);
        assertThat(getPlatformAdminsRepository.countPlatformAdmins()).isEqualTo(1);
        assertThat(isEnabledPlatformAdmin(second)).isTrue();
    }

    private User newEnabledPlatformAdmin() {
        User user = UserMother.randomUser();
        user.setEnabled(true);
        createUserRepository.create(user);
        users.add(user.getId());
        managePlatformAdminRepository.grant(UserId.of(user.getId()));
        return user;
    }

    private boolean isEnabledPlatformAdmin(User user) {
        return getPlatformAdminsRepository.findAll().stream()
                .anyMatch(admin -> admin.getId().equals(user.getId()) && admin.isEnabled());
    }

    private record EnabledPlatformAdmin(UUID id, Timestamp disabledAt) {
    }
}
