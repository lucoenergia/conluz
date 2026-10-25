package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.config.DisableHuaweiConfigurationService;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfiguration;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationRepository;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationService;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.lucoenergia.conluz.domain.admin.community.CommunityMother.DEFAULT_COMMUNITY_ID;

/**
 * Commits, since the race it covers is between two transactions. The configuration and its plant are created the
 * way production creates them, and deleted afterwards.
 */
class SetHuaweiConfigurationServiceIntegrationTest extends BaseIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private SetHuaweiConfigurationService setHuaweiConfigurationService;
    @Autowired
    private DisableHuaweiConfigurationService disableHuaweiConfigurationService;
    @Autowired
    private GetHuaweiConfigRepository getHuaweiConfigRepository;
    @Autowired
    private SetHuaweiConfigurationRepository setHuaweiConfigurationRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
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

    /**
     * The write runs first and holds its transaction open; the disable starts while it does. With the lock, the
     * disable waits on the row and applies after the write commits. Without it, the disable commits at once and the
     * write, committing after it, writes back the {@code enabled} it read: the configuration ends up enabled.
     */
    @Test
    @DisplayName("HCF-006 a disable issued while a configuration write is in progress is applied, not reverted by the write")
    void disableDuringWriteIsNotReverted() throws Exception {
        createEnabledConfiguration();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        AtomicReference<Future<?>> disabling = new AtomicReference<>();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                setHuaweiConfigurationService.setHuaweiConfiguration(plantId, new SetHuaweiConfiguration.Builder()
                        .withUsername("newUser")
                        .withBaseUrl(HuaweiConfig.DEFAULT_BASE_URL)
                        .build());
                disabling.set(pool.submit(() -> disableHuaweiConfigurationService.disable(plantId)));
                awaitWaitingOnALockOrDone(disabling.get());
            });
            disabling.get().get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        HuaweiConfig stored = getHuaweiConfigRepository.getHuaweiConfig(plantId).orElseThrow();
        assertThat(stored.getEnabled()).isFalse();
        assertThat(stored.getUsername()).isEqualTo("newUser");
    }

    /**
     * Returns once the disable is blocked on a row lock, or has finished because nothing blocked it. Reading
     * {@code pg_stat_activity} rather than sleeping makes the order of the two transactions certain.
     */
    private void awaitWaitingOnALockOrDone(Future<?> disabling) {
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (!disabling.isDone() && !anUpdateOfTheConfigurationIsWaitingOnALock()) {
            if (Instant.now().isAfter(deadline)) {
                fail("The disable neither finished nor waited on a lock within " + TIMEOUT);
            }
            Thread.onSpinWait();
        }
    }

    /**
     * Runs inside the write's transaction, where PostgreSQL would otherwise answer every poll from the activity
     * snapshot it took on the first one; clearing it makes each poll see the disable's current state.
     */
    private boolean anUpdateOfTheConfigurationIsWaitingOnALock() {
        jdbcTemplate.execute("SELECT pg_stat_clear_snapshot()");
        Integer waiting = jdbcTemplate.queryForObject("SELECT count(*) FROM pg_stat_activity "
                + "WHERE wait_event_type = 'Lock' AND query ILIKE 'update huawei_config%'", Integer.class);
        return waiting != null && waiting > 0;
    }

    private void createEnabledConfiguration() {
        User user = createUserRepository.create(UserMother.randomUser());
        userId = user.getId();
        Supply supply = createSupplyRepository.create(SupplyMother.random().build(), UserId.of(userId), DEFAULT_COMMUNITY_ID);
        supplyId = supply.getId();
        plantId = createPlantRepository.create(PlantMother.random(supply).build(), SupplyId.of(supplyId)).getId();
        setHuaweiConfigurationRepository.setHuaweiConfiguration(plantId, new HuaweiConfig.Builder()
                .setUsername("seedUser")
                .setPassword("seedPassword")
                .setBaseUrl(HuaweiConfig.DEFAULT_BASE_URL)
                .setEnabled(Boolean.TRUE)
                .build());
    }
}
