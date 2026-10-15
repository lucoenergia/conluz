package org.lucoenergia.conluz.infrastructure.datadis.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.LockDatadisConfigRepository;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationRepository;
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
 * Commits, since the lock must be seen from another transaction. The configuration and its community are created
 * the way production creates them, and deleted afterwards.
 */
class LockDatadisConfigRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private LockDatadisConfigRepository repository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private SetDatadisConfigurationRepository setDatadisConfigurationRepository;
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

    @Test
    void aLockOutsideATransaction_isRefused_becauseItWouldBeReleasedAtOnce() {
        assertThatThrownBy(() -> repository.lock(UUID.randomUUID()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void aCommunityWithoutConfiguration_isNotLocked() {
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
            assertThat(repository.lock(communityId)).isTrue();

            assertThatThrownBy(() -> other.executeWithoutResult(inner -> lockWithoutWaiting()))
                    .isInstanceOf(DataAccessException.class);
        });

        other.executeWithoutResult(inner -> lockWithoutWaiting());
    }

    private void lockWithoutWaiting() {
        jdbcTemplate.queryForObject("SELECT id FROM datadis_config WHERE community_id = ? FOR NO KEY UPDATE NOWAIT",
                UUID.class, communityId);
    }

    private void createConfiguration() {
        communityId = createCommunityRepository.create(CommunityMother.random().build()).getId();
        setDatadisConfigurationRepository.setDatadisConfiguration(communityId, new DatadisConfig.Builder()
                .setUsername("user")
                .setPassword("password")
                .setBaseUrl(DatadisConfig.DEFAULT_BASE_URL)
                .setEnabled(Boolean.TRUE)
                .build());
    }
}
