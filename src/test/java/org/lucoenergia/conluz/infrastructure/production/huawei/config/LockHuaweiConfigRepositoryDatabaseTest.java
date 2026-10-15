package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.config.LockHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationRepository;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
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
 * Commits, since the lock must be seen from another transaction. The configuration and its plant are created the
 * way production creates them, and deleted afterwards.
 */
class LockHuaweiConfigRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private LockHuaweiConfigRepository repository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private SetHuaweiConfigurationRepository setHuaweiConfigurationRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID userId;
    private UUID supplyId;
    private UUID plantId;

    @AfterEach
    void deleteTheConfiguration() {
        if (plantId != null) {
            jdbcTemplate.update("DELETE FROM huawei_config WHERE plant_id = ?", plantId);
            jdbcTemplate.update("DELETE FROM plants WHERE id = ?", plantId);
        }
        if (supplyId != null) {
            jdbcTemplate.update("DELETE FROM supplies WHERE id = ?", supplyId);
        }
        if (userId != null) {
            jdbcTemplate.update("DELETE FROM community_memberships WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    @Test
    void aLockOutsideATransaction_isRefused_becauseItWouldBeReleasedAtOnce() {
        assertThatThrownBy(() -> repository.lock(UUID.randomUUID()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void aPlantWithoutConfiguration_isNotLocked() {
        Boolean locked = new TransactionTemplate(transactionManager)
                .execute(status -> repository.lock(UUID.randomUUID()));

        assertThat(locked).isFalse();
    }

    @Test
    void aConfiguration_isLockedUntilTheEndOfTheTransaction() {
        createConfiguration();
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate other = new TransactionTemplate(transactionManager);
        other.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        outer.executeWithoutResult(status -> {
            assertThat(repository.lock(plantId)).isTrue();

            assertThatThrownBy(() -> other.executeWithoutResult(inner -> lockWithoutWaiting()))
                    .isInstanceOf(DataAccessException.class);
        });

        other.executeWithoutResult(inner -> lockWithoutWaiting());
    }

    private void lockWithoutWaiting() {
        jdbcTemplate.queryForObject("SELECT id FROM huawei_config WHERE plant_id = ? FOR NO KEY UPDATE NOWAIT",
                UUID.class, plantId);
    }

    private void createConfiguration() {
        User user = createUserRepository.create(UserMother.randomUser());
        userId = user.getId();
        Supply supply = createSupplyRepository.create(SupplyMother.random().build(), UserId.of(userId));
        supplyId = supply.getId();
        plantId = createPlantRepository.create(PlantMother.random(supply).build(), SupplyId.of(supplyId)).getId();
        setHuaweiConfigurationRepository.setHuaweiConfiguration(plantId, new HuaweiConfig.Builder()
                .setUsername("user")
                .setPassword("password")
                .setBaseUrl(HuaweiConfig.DEFAULT_BASE_URL)
                .setEnabled(Boolean.TRUE)
                .build());
    }
}
