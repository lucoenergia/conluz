package org.lucoenergia.conluz.infrastructure.admin.user.lock;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LockUserRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private LockUserRepository repository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void aLockOutsideATransaction_isRefused_becauseItWouldBeReleasedAtOnce() {
        UserId anyone = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> repository.lock(anyone)).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void anUnknownUser_isNotLocked() {
        Boolean locked = new TransactionTemplate(transactionManager)
                .execute(status -> repository.lock(UserId.of(UUID.randomUUID())));

        assertThat(locked).isFalse();
    }
}
