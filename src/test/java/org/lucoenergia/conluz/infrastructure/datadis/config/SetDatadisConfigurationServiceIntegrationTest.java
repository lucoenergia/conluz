package org.lucoenergia.conluz.infrastructure.datadis.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.DisableDatadisConfigurationService;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfiguration;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationRepository;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationService;
import org.lucoenergia.conluz.domain.datadis.get.GetDatadisConfigRepository;
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

/**
 * Commits, since the race it covers is between two transactions. The configuration and its community are created
 * the way production creates them, and deleted afterwards.
 */
class SetDatadisConfigurationServiceIntegrationTest extends BaseIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private SetDatadisConfigurationService setDatadisConfigurationService;
    @Autowired
    private DisableDatadisConfigurationService disableDatadisConfigurationService;
    @Autowired
    private GetDatadisConfigRepository getDatadisConfigRepository;
    @Autowired
    private SetDatadisConfigurationRepository setDatadisConfigurationRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID communityId;

    @AfterEach
    void deleteTheConfiguration() {
        if (communityId != null) {
            jdbcTemplate.update("DELETE FROM datadis_config WHERE community_id = ?", communityId);
            jdbcTemplate.update("DELETE FROM communities WHERE id = ?", communityId);
        }
    }

    /**
     * The write runs first and holds its transaction open; the disable starts while it does. With the lock, the
     * disable waits on the row and applies after the write commits. Without it, the disable commits at once and the
     * write, committing after it, writes back the {@code enabled} it read: the configuration ends up enabled.
     */
    @Test
    @DisplayName("DCF-006 a disable issued while a configuration write is in progress is applied, not reverted by the write")
    void disableDuringWriteIsNotReverted() throws Exception {
        createEnabledConfiguration();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        AtomicReference<Future<?>> disabling = new AtomicReference<>();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                setDatadisConfigurationService.setDatadisConfiguration(communityId,
                        new SetDatadisConfiguration.Builder()
                                .withUsername("newUser")
                                .withBaseUrl(DatadisConfig.DEFAULT_BASE_URL)
                                .build());
                disabling.set(pool.submit(() -> disableDatadisConfigurationService.disable(communityId)));
                awaitWaitingOnALockOrDone(disabling.get());
            });
            disabling.get().get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        DatadisConfig stored = getDatadisConfigRepository.findByCommunityId(communityId).orElseThrow();
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
                + "WHERE wait_event_type = 'Lock' AND query ILIKE 'update datadis_config%'", Integer.class);
        return waiting != null && waiting > 0;
    }

    private void createEnabledConfiguration() {
        communityId = createCommunityRepository.create(CommunityMother.random().build()).getId();
        setDatadisConfigurationRepository.setDatadisConfiguration(communityId, new DatadisConfig.Builder()
                .setUsername("seedUser")
                .setPassword("seedPassword")
                .setBaseUrl(DatadisConfig.DEFAULT_BASE_URL)
                .setEnabled(Boolean.TRUE)
                .build());
    }
}
